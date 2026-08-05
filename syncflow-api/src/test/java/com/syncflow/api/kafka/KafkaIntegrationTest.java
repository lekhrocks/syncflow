package com.syncflow.api.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
@Testcontainers
class KafkaIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.0"));

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String PIPELINE = "pipe-1";
    private static final String TABLE = "users";

    private KafkaProperties kafkaProperties;
    private String bootstrapServers;

    @BeforeEach
    void setUp() {
        bootstrapServers = KAFKA.getBootstrapServers();
        kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(bootstrapServers);
    }

    @Test
    void publisherWritesEventToTopicAndConsumerReadsItBack() throws Exception {
        var publisher = new KafkaEventPublisher(PIPELINE, kafkaProperties, MAPPER,
                new SimpleMeterRegistry());
        var event = createEvent("evt-1", "id", 1);

        publisher.publish(event);
        publisher.flush();
        publisher.close();

        // read it back with a raw consumer
        var topic = kafkaProperties.getTopic().topicFor(PIPELINE, TABLE);
        try (var consumer = consumer(bootstrapServers)) {
            consumer.subscribe(List.of(topic));
            var record = await()
                    .atMost(Duration.ofSeconds(15))
                    .until(() -> {
                        var rs = consumer.poll(Duration.ofMillis(200));
                        return rs.isEmpty() ? null : rs.records(topic).iterator().next();
                    }, java.util.Objects::nonNull);

            assertEquals(topic, record.topic());
            assertEquals("{\"id\":1}", record.key()); // PK map serialized as JSON
            var eventIdHeader = record.headers().lastHeader("eventId");
            assertNotNull(eventIdHeader);
            assertEquals("evt-1", new String(eventIdHeader.value()));
            var roundTripped = MAPPER.readValue(record.value(), CDCEvent.class);
            assertEquals("evt-1", roundTripped.header().eventId());
            assertEquals(TABLE, roundTripped.source().table());
        }
    }

    @Test
    void topicProvisionerCreatesIdempotentTopics() throws Exception {
        var provisioner = new KafkaTopicProvisioner(kafkaProperties);

        provisioner.provisionTopics(PIPELINE, List.of(TABLE));
        provisioner.provisionTopics(PIPELINE, List.of(TABLE)); // second call must not fail

        var topic = kafkaProperties.getTopic().topicFor(PIPELINE, TABLE);
        try (var admin = org.apache.kafka.clients.admin.AdminClient.create(
                Map.of(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers))) {
            var names = admin.listTopics().names().get(10, TimeUnit.SECONDS);
            assertTrue(names.contains(topic), "expected topic " + topic + " to exist, got " + names);
        }
    }

    @Test
    void kafkaCdcConsumerBridgesEventsToSyncOrchestrator() throws Exception {
        var orchestrator = mock(com.syncflow.api.sync.SyncOrchestrator.class);
        var consumer = new KafkaCdcConsumer(kafkaProperties, orchestrator, MAPPER,
                new SimpleMeterRegistry());

        // pre-create the topic so the consumer's subscribe pattern matches
        new KafkaTopicProvisioner(kafkaProperties).provisionTopics(PIPELINE, List.of(TABLE));

        consumer.startConsuming(PIPELINE);
        try {
            var publisher = new KafkaEventPublisher(PIPELINE, kafkaProperties, MAPPER,
                    new SimpleMeterRegistry());
            publisher.publish(createEvent("evt-bridge", "id", 42));
            publisher.flush();
            publisher.close();

            var captor = ArgumentCaptor.forClass(CDCEvent.class);
            verify(orchestrator, timeout(15_000).atLeast(1))
                    .submitEvent(org.mockito.ArgumentMatchers.eq(PIPELINE), captor.capture());
            assertEquals("evt-bridge", captor.getValue().header().eventId());
        } finally {
            consumer.stopConsuming(PIPELINE);
        }
    }

    @Test
    void kafkaConnectorValidateAgainstLiveBroker() {
        var connector = new com.syncflow.connector.kafka.KafkaConnector();
        var props = Map.of("bootstrap.servers", bootstrapServers);
        var ctx = new com.syncflow.core.spi.ConnectorContext(
                new com.syncflow.core.model.ConnectionConfiguration(
                        com.syncflow.core.model.ConnectorType.KAFKA,
                        "localhost", 9092, "kafka", "user", "pass", props),
                Map.of());
        var result = connector.validate(ctx);
        assertTrue(result.valid(), "expected validation to pass against live broker, got " + result.errors());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static KafkaConsumer<String, String> consumer(String bootstrapServers) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-it-" + System.nanoTime());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }

    private static CDCEvent createEvent(String eventId, String pkColumn, Object pkValue) {
        return new CDCEvent(
                new EventHeader(eventId, PIPELINE, "conn-1", 1, 1, Map.of()),
                new EventSource("db", "public", TABLE, "postgresql"),
                CDCOperation.INSERT,
                new EventPayload(null, Map.of("id", pkValue, "name", "test"), Map.of(pkColumn, pkValue)),
                new EventMetadata(1, Instant.now(), 0), null,
                new OffsetInformation("PG", Map.of("lsn", "123"), "", Instant.now()));
    }
}
