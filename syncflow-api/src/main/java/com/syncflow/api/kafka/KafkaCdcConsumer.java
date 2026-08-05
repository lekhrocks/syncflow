package com.syncflow.api.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.cdc.CDCEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Kafka CDC consumer — polls events from topics matching
 * {@code {prefix}.{pipelineId}.*} and submits them to
 * {@link SyncOrchestrator} for transform + write.
 *
 * One consumer instance is created per pipeline start.
 * Runs on a dedicated virtual thread so it never blocks the main thread pool.
 */
@Component
@ConditionalOnProperty(name = "syncflow.kafka.enabled", havingValue = "true")
public class KafkaCdcConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaCdcConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final KafkaProperties kafkaProperties;
    private final SyncOrchestrator syncOrchestrator;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /** Active consumer handles keyed by pipelineId. */
    private final Map<String, ConsumerHandle> handles = new ConcurrentHashMap<>();

    public KafkaCdcConsumer(KafkaProperties kafkaProperties,
            SyncOrchestrator syncOrchestrator,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.kafkaProperties = kafkaProperties;
        this.syncOrchestrator = syncOrchestrator;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Start consuming events for {@code pipelineId} from all topics matching
     * {@code {prefix}.{pipelineId}.*}.
     */
    public void startConsuming(String pipelineId) {
        if (handles.containsKey(pipelineId)) {
            log.debug("Kafka consumer already running for pipeline={}", pipelineId);
            return;
        }

        var consumer = buildConsumer(pipelineId);
        var pattern = Pattern.compile(
                Pattern.quote(kafkaProperties.getTopic().getPrefix() + "." + pipelineId + ".") + ".*");
        consumer.subscribe(pattern);

        var running = new AtomicBoolean(true);
        var thread = Thread.startVirtualThread(() -> pollLoop(pipelineId, consumer, running));
        handles.put(pipelineId, new ConsumerHandle(consumer, running, thread));
        log.info("Kafka consumer started for pipeline={} pattern={}", pipelineId, pattern);
    }

    /** Stop consuming events for {@code pipelineId}. */
    public void stopConsuming(String pipelineId) {
        var handle = handles.remove(pipelineId);
        if (handle == null)
            return;

        handle.running().set(false);
        handle.consumer().wakeup(); // unblocks the poll() call
        try {
            handle.thread().join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Kafka consumer stopped for pipeline={}", pipelineId);
    }

    /** Stop all active consumers (called on application shutdown). */
    public void stopAll() {
        handles.keySet().forEach(this::stopConsuming);
    }

    public boolean isConsuming(String pipelineId) {
        return handles.containsKey(pipelineId);
    }

    // ── poll loop ─────────────────────────────────────────────────────────────

    private void pollLoop(String pipelineId,
            KafkaConsumer<String, String> consumer,
            AtomicBoolean running) {
        try {
            while (running.get()) {
                var records = consumer.poll(POLL_TIMEOUT);
                if (records.isEmpty())
                    continue;

                for (var record : records) {
                    try {
                        var event = objectMapper.readValue(record.value(), CDCEvent.class);
                        syncOrchestrator.submitEvent(pipelineId, event);
                        meterRegistry.counter("syncflow.kafka.consume.success",
                                "pipeline", pipelineId,
                                "topic", record.topic()).increment();
                    } catch (Exception e) {
                        log.error("Failed to deserialize/submit Kafka record pipeline={} topic={} offset={}",
                                pipelineId, record.topic(), record.offset(), e);
                        meterRegistry.counter("syncflow.kafka.consume.error",
                                "pipeline", pipelineId).increment();
                    }
                }

                // manual commit after successful batch processing
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            // expected on stopConsuming() — not an error
            log.debug("Kafka consumer woken up for pipeline={}", pipelineId);
        } catch (Exception e) {
            log.error("Kafka consumer loop failed for pipeline={}", pipelineId, e);
            meterRegistry.counter("syncflow.kafka.consumer.failure",
                    "pipeline", pipelineId).increment();
        } finally {
            consumer.close();
            log.debug("Kafka consumer closed for pipeline={}", pipelineId);
        }
    }

    // ── factory ───────────────────────────────────────────────────────────────

    private KafkaConsumer<String, String> buildConsumer(String pipelineId) {
        var cfg = kafkaProperties.getConsumer();
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                cfg.getGroupId() + "-" + pipelineId); // unique group per pipeline
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, cfg.getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, cfg.isEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, cfg.getMaxPollRecords());
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new KafkaConsumer<>(props);
    }

    private record ConsumerHandle(
            KafkaConsumer<String, String> consumer,
            AtomicBoolean running,
            Thread thread) {
    }
}
