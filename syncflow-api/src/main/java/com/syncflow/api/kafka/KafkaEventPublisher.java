package com.syncflow.api.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.publisher.EventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link EventPublisher} implementation that publishes {@link CDCEvent} records
 * to Kafka topics as JSON.
 * <p>
 * Topic naming: {@code {prefix}.{pipelineId}.{table}}
 * Message key: primary key map serialized as JSON (ensures same-row events land
 * on the same partition)
 */
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaProducer<String, String> producer;
    private final KafkaProperties kafkaProperties;
    private final String pipelineId;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AtomicLong publishedCount = new AtomicLong(0);

    public KafkaEventPublisher(String pipelineId,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.pipelineId = pipelineId;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.producer = buildProducer(kafkaProperties);
    }

    @Override
    public void publish(CDCEvent event) {
        try {
            var topic = kafkaProperties.getTopic().topicFor(pipelineId, event.source().table());
            var key = serializeKey(event);
            var value = objectMapper.writeValueAsString(event);

            var record = new ProducerRecord<>(topic, key, value);
            // add CDC metadata as Kafka headers for downstream consumers
            record.headers()
                    .add("eventId", event.header().eventId().getBytes())
                    .add("operation", event.operation().name().getBytes())
                    .add("pipelineId", pipelineId.getBytes())
                    .add("table", event.source().table().getBytes());

            producer.send(record, (metadata, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish CDC event id={} to topic={} pipeline={}",
                            event.header().eventId(), topic, pipelineId, ex);
                    meterRegistry.counter("syncflow.kafka.publish.error",
                            "pipeline", pipelineId, "topic", topic).increment();
                } else {
                    publishedCount.incrementAndGet();
                    log.debug("Published event id={} to topic={} partition={} offset={}",
                            event.header().eventId(), topic,
                            metadata.partition(), metadata.offset());
                    meterRegistry.counter("syncflow.kafka.publish.success",
                            "pipeline", pipelineId, "topic", topic).increment();
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CDC event id={} for pipeline={}",
                    event.header().eventId(), pipelineId, e);
            meterRegistry.counter("syncflow.kafka.serialize.error",
                    "pipeline", pipelineId).increment();
        }
    }

    @Override
    public void flush() {
        producer.flush();
        log.debug("Kafka producer flushed for pipeline={}", pipelineId);
    }

    @Override
    public long count() {
        return publishedCount.get();
    }

    @Override
    public void close() {
        flush();
        producer.close();
        log.info("Kafka producer closed for pipeline={}", pipelineId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String serializeKey(CDCEvent event) {
        try {
            var pk = event.payload().primaryKeys();
            return pk.isEmpty()
                    ? event.header().eventId()
                    : objectMapper.writeValueAsString(pk);
        } catch (JsonProcessingException e) {
            return event.header().eventId();
        }
    }

    private static KafkaProducer<String, String> buildProducer(KafkaProperties props) {
        var p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, props.getProducer().getAcks());
        p.put(ProducerConfig.RETRIES_CONFIG, props.getProducer().getRetries());
        p.put(ProducerConfig.BATCH_SIZE_CONFIG, props.getProducer().getBatchSize());
        p.put(ProducerConfig.LINGER_MS_CONFIG, props.getProducer().getLingerMs());
        p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, props.getProducer().getCompressionType());
        // idempotent producer: guarantees exactly-once delivery to the broker
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        p.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new KafkaProducer<>(p);
    }
}
