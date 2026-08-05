package com.syncflow.api.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.kafka.KafkaCdcConsumer;
import com.syncflow.api.kafka.KafkaEventPublisher;
import com.syncflow.api.kafka.KafkaProperties;
import com.syncflow.api.kafka.KafkaTopicProvisioner;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.cdc.publisher.BoundedQueueEventPublisher;
import com.syncflow.core.cdc.publisher.EventPublisher;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.spi.CdcCapableConnector;
import com.syncflow.core.spi.ConnectorContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CaptureLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CaptureLifecycle.class);

    private final PipelineDesignerService pipelineService;
    private final ConnectionService connectionService;
    private final ConnectorRegistry connectorRegistry;
    private final OffsetStore offsetStore;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Optional Kafka components — only present when syncflow.kafka.enabled=true
    private final Optional<KafkaProperties> kafkaProperties;
    private final Optional<KafkaTopicProvisioner> topicProvisioner;
    private final Optional<KafkaCdcConsumer> kafkaCdcConsumer;

    private final Map<String, CaptureEntry> activeCaptures = new ConcurrentHashMap<>();

    public CaptureLifecycle(PipelineDesignerService pipelineService,
            ConnectionService connectionService,
            ConnectorRegistry connectorRegistry,
            OffsetStore offsetStore,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            Optional<KafkaProperties> kafkaProperties,
            Optional<KafkaTopicProvisioner> topicProvisioner,
            Optional<KafkaCdcConsumer> kafkaCdcConsumer) {
        this.pipelineService = pipelineService;
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.offsetStore = offsetStore;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.kafkaProperties = kafkaProperties;
        this.topicProvisioner = topicProvisioner;
        this.kafkaCdcConsumer = kafkaCdcConsumer;
    }

    public CaptureStatus start(String pipelineId, String tableOrCollection) {
        var existing = activeCaptures.get(pipelineId);
        if (existing != null && existing.connector().captureStatus() != CaptureStatus.INACTIVE) {
            return existing.connector().captureStatus();
        }

        var pipeline = pipelineService.get(pipelineId);
        var conn = connectionService.getWithDecryptedCredentials(pipeline.source().connectionId());
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var connector = connectorRegistry.get(ct)
                .filter(c -> c instanceof CdcCapableConnector)
                .map(c -> (CdcCapableConnector) c)
                .orElseThrow(() -> new IllegalArgumentException("No CDC connector for type: " + ct));

        var config = toConfig(conn);
        var ctx = new ConnectorContext(config, Map.of());

        // pre-flight validation
        var validation = connector.validate(ctx);
        if (!validation.valid()) {
            log.error("CDC pre-flight validation failed for pipeline={}: {}",
                    pipelineId, validation.errors());
            throw new IllegalStateException(
                    "CDC pre-flight validation failed: " + String.join(", ", validation.errors()));
        }

        // build the correct publisher — Kafka if enabled, bounded queue otherwise
        var publisher = buildPublisher(pipelineId, pipeline.tableMappings());
        var meterReg = this.meterRegistry;

        connector.startCDC(ctx, event -> {
            publisher.publish(event);
            meterReg.counter("syncflow.cdc.events",
                    "pipeline", pipelineId,
                    "operation", event.operation().name()).increment();
        });

        // task #7: start Kafka consumer bridge when Kafka is enabled
        kafkaCdcConsumer.ifPresent(c -> c.startConsuming(pipelineId));

        var savedOffset = offsetStore.get(pipelineId);
        if (!savedOffset.isEmpty()) {
            log.info("CDC resuming from saved offset for pipeline={} offset={}", pipelineId, savedOffset);
        } else {
            log.info("CDC starting fresh (no saved offset) for pipeline={}", pipelineId);
        }

        activeCaptures.put(pipelineId, new CaptureEntry(connector, publisher, pipelineId));
        log.info("CDC started for pipeline={} connectorType={} kafka={}",
                pipelineId, ct, kafkaProperties.map(KafkaProperties::isEnabled).orElse(false));
        return CaptureStatus.RUNNING;
    }

    public void stop(String pipelineId) {
        var entry = activeCaptures.get(pipelineId);
        if (entry != null) {
            if (entry.connector() != null) {
                var offset = entry.connector().currentOffset();
                if (!offset.isEmpty()) {
                    offsetStore.save(pipelineId, offset);
                    log.info("CDC offset saved for pipeline={} offset={}", pipelineId, offset);
                }
                entry.connector().stopCDC();
            }
            // flush Kafka producer before closing
            try {
                entry.publisher().flush();
            } catch (Exception ignored) {
            }
            try {
                entry.publisher().close();
            } catch (Exception ignored) {
            }
            // stop Kafka consumer
            kafkaCdcConsumer.ifPresent(c -> c.stopConsuming(pipelineId));
            activeCaptures.remove(pipelineId);
        }
    }

    public void pause(String pipelineId) {
        var entry = activeCaptures.get(pipelineId);
        if (entry != null && entry.connector() != null) {
            entry.connector().pauseCDC();
            log.debug("CDC paused for pipeline={}", pipelineId);
        }
    }

    public void resume(String pipelineId) {
        var entry = activeCaptures.get(pipelineId);
        if (entry != null && entry.connector() != null) {
            entry.connector().resumeCDC();
            log.debug("CDC resumed for pipeline={}", pipelineId);
        }
    }

    public CaptureStatus status(String pipelineId) {
        var entry = activeCaptures.get(pipelineId);
        if (entry == null)
            return CaptureStatus.INACTIVE;
        return entry.connector() != null ? entry.connector().captureStatus() : CaptureStatus.INACTIVE;
    }

    public long eventCount(String pipelineId) {
        var entry = activeCaptures.get(pipelineId);
        if (entry == null || entry.publisher() == null)
            return 0;
        return entry.publisher().count();
    }

    public void shutdownAll() {
        activeCaptures.values().forEach(e -> {
            if (e.connector() != null) {
                var offset = e.connector().currentOffset();
                if (!offset.isEmpty())
                    offsetStore.save(e.pipelineId(), offset);
                e.connector().stopCDC();
            }
            try {
                e.publisher().flush();
            } catch (Exception ignored) {
            }
            try {
                e.publisher().close();
            } catch (Exception ignored) {
            }
            kafkaCdcConsumer.ifPresent(c -> c.stopConsuming(e.pipelineId()));
        });
        activeCaptures.clear();
        log.info("All CDC captures shut down");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Task #7 bridge: when Kafka is enabled, provision topics and return a
     * KafkaEventPublisher; otherwise fall back to BoundedQueueEventPublisher.
     */
    private EventPublisher buildPublisher(String pipelineId, List<TableMapping> tableMappings) {
        if (kafkaProperties.map(KafkaProperties::isEnabled).orElse(false)) {
            var props = kafkaProperties.get();
            var tables = tableMappings.stream()
                    .map(TableMapping::sourceTable)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
            topicProvisioner.ifPresent(p -> p.provisionTopics(pipelineId, tables));
            log.info("Using KafkaEventPublisher for pipeline={} tables={}", pipelineId, tables);
            return new KafkaEventPublisher(pipelineId, props, objectMapper, meterRegistry);
        }
        log.info("Using BoundedQueueEventPublisher for pipeline={} (Kafka disabled)", pipelineId);
        return new BoundedQueueEventPublisher();
    }

    private ConnectionConfiguration toConfig(com.syncflow.core.connection.Connection conn) {
        var p = conn.getProperties();
        var c = conn.getCredentials();
        return new ConnectionConfiguration(
                ConnectorTypeMapper.toCore(p.type()),
                p.host(), p.port(), p.database(),
                c.username(), c.password(), p.options());
    }

    private record CaptureEntry(CdcCapableConnector connector, EventPublisher publisher, String pipelineId) {
    }
}
