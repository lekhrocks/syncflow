package com.syncflow.api.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.persistence.cdc.entity.ActiveCaptureEntity;
import com.syncflow.persistence.cdc.repository.ActiveCaptureRepository;
import com.syncflow.api.connection.ConnectionMapper;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.kafka.KafkaCdcConsumer;
import com.syncflow.api.kafka.KafkaEventPublisher;
import com.syncflow.api.kafka.KafkaProperties;
import com.syncflow.api.kafka.KafkaTopicProvisioner;
import com.syncflow.api.lock.DistributedLockService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.cdc.publisher.BoundedQueueEventPublisher;
import com.syncflow.core.cdc.publisher.CircuitBreakerEventPublisher;
import com.syncflow.core.cdc.publisher.EventPublisher;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.spi.CdcCapableConnector;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
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
    private final ActiveCaptureRepository activeCaptureRepo;
    private final DistributedLockService lockService;

    // Optional Kafka components — only present when syncflow.kafka.enabled=true
    private final Optional<KafkaProperties> kafkaProperties;
    private final Optional<KafkaTopicProvisioner> topicProvisioner;
    private final Optional<KafkaCdcConsumer> kafkaCdcConsumer;

    // Fast-path in-memory cache of active captures; the durable state lives
    // in active_captures table. Reads (status, eventCount) go through the
    // cache; writes round-trip to Postgres via the repository. This keeps
    // request latency low for the hot path without losing state on restart.
    private final Map<String, CaptureEntry> activeCaptures = new ConcurrentHashMap<>();

    private static String tenantKey(String tenantId, String pipelineId) {
        return tenantId + ":" + pipelineId;
    }

    public CaptureLifecycle(PipelineDesignerService pipelineService,
            ConnectionService connectionService,
            ConnectorRegistry connectorRegistry,
            OffsetStore offsetStore,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            ActiveCaptureRepository activeCaptureRepo,
            DistributedLockService lockService,
            Optional<KafkaProperties> kafkaProperties,
            Optional<KafkaTopicProvisioner> topicProvisioner,
            Optional<KafkaCdcConsumer> kafkaCdcConsumer) {
        this.pipelineService = pipelineService;
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.offsetStore = offsetStore;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.activeCaptureRepo = activeCaptureRepo;
        this.lockService = lockService;
        this.kafkaProperties = kafkaProperties;
        this.topicProvisioner = topicProvisioner;
        this.kafkaCdcConsumer = kafkaCdcConsumer;
    }

    public CaptureStatus start(String pipelineId, String tableOrCollection, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        // Distributed lock prevents two pods from starting CDC for the same
        // pipeline concurrently (which would create duplicate slots / races).
        return lockService.withLock("capture:" + tenantKey(tenantContext.tenantId().value(), pipelineId),
                tenantContext,
                Duration.ofSeconds(30),
                () -> doStart(pipelineId, tableOrCollection, tenantContext));
    }

    private CaptureStatus doStart(String pipelineId, String tableOrCollection, TenantContext tenantContext) {
        // Include tenant in key so multiple tenants can have pipelines with same ID
        var key = tenantKey(tenantContext.tenantId().value(), pipelineId);
        var existing = activeCaptures.get(key);
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

        var config = ConnectionMapper.toConfig(conn);
        // Key the Debezium offset file (and any connector state) per pipeline so
        // multiple pipelines on the same database don't share/corrupt position.
        // Include tenant_id for partition routing in the debezium_offsets table (V16).
        var ctx = new ConnectorContext(config, Map.of(
                "pipelineId", pipelineId,
                "tenantId", tenantContext.tenantId().value()));

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
        kafkaCdcConsumer.ifPresent(c -> c.startConsuming(pipelineId, tenantContext));

        var savedOffset = offsetStore.get(key);
        if (!savedOffset.isEmpty()) {
            log.info("CDC resuming from saved offset for pipeline={} offset={}", pipelineId, savedOffset);
        } else {
            log.info("CDC starting fresh (no saved offset) for pipeline={}", pipelineId);
        }

        activeCaptures.put(key, new CaptureEntry(connector, publisher, pipelineId,
                tenantContext.tenantId().value()));
        // Persist active capture to DB so a pod restart can resume.
        persistActiveCapture(key, tenantContext.tenantId().value(), pipelineId, CaptureStatus.RUNNING, Map.of());
        log.info("CDC started for pipeline={} connectorType={} kafka={}",
                pipelineId, ct, kafkaProperties.map(KafkaProperties::isEnabled).orElse(false));
        return CaptureStatus.RUNNING;
    }

    public void stop(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = tenantKey(tenantContext.tenantId().value(), pipelineId);
        var entry = activeCaptures.get(key);
        if (entry != null) {
            if (entry.connector() != null) {
                var offset = entry.connector().currentOffset();
                if (!offset.isEmpty()) {
                    offsetStore.save(key, offset);
                    // Persist the final offset to the durable capture record.
                    persistActiveCapture(key, tenantContext.tenantId().value(), pipelineId,
                            CaptureStatus.INACTIVE, offset);
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
            activeCaptures.remove(key);
        }
    }

    public void pause(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = tenantKey(tenantContext.tenantId().value(), pipelineId);
        var entry = activeCaptures.get(key);
        if (entry != null && entry.connector() != null) {
            entry.connector().pauseCDC();
            log.debug("CDC paused for pipeline={}", pipelineId);
        }
    }

    public void resume(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = tenantKey(tenantContext.tenantId().value(), pipelineId);
        var entry = activeCaptures.get(key);
        if (entry != null && entry.connector() != null) {
            entry.connector().resumeCDC();
            log.debug("CDC resumed for pipeline={}", pipelineId);
        }
    }

    public CaptureStatus status(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = tenantKey(tenantContext.tenantId().value(), pipelineId);
        var entry = activeCaptures.get(key);
        if (entry == null)
            return CaptureStatus.INACTIVE;
        return entry.connector() != null ? entry.connector().captureStatus() : CaptureStatus.INACTIVE;
    }

    public long eventCount(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = tenantKey(tenantContext.tenantId().value(), pipelineId);
        var entry = activeCaptures.get(key);
        if (entry == null || entry.publisher() == null)
            return 0;
        return entry.publisher().count();
    }

    /**
     * Graceful shutdown for a single tenant. Stops every active CDC capture
     * belonging to {@code tenantContext}, persists the offset, and flushes
     * the publisher. Use this on tenant-scope lifecycle events.
     */
    public void shutdownForTenant(TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var tenantId = tenantContext.tenantId().value();
        var it = activeCaptures.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            // activeCaptures key is "tenantId:pipelineId" — only close ours.
            if (!entry.getKey().startsWith(tenantId + ":"))
                continue;
            shutdownOne(entry.getValue());
            it.remove();
        }
        log.info("CDC captures shut down for tenant={}", tenantId);
    }

    /**
     * Global shutdown (SIGTERM, JVM exit). Closes ALL captures regardless of
     * tenant — this is operator-only and should never run from a request thread.
     * Requires explicit confirmation via the {@link #CONFIRM_GLOBAL_SHUTDOWN}
     * argument so callers cannot invoke it by accident.
     */
    public void shutdownAllGlobal(GlobalShutdown confirmation) {
        if (confirmation == null) {
            throw new IllegalArgumentException(
                    "Global shutdown requires explicit confirmation token");
        }
        activeCaptures.values().forEach(this::shutdownOne);
        activeCaptures.clear();
        log.warn("All CDC captures shut down globally ({} captures)", activeCaptures.size());
    }

    /**
     * stop all CDC captures on JVM shutdown so Debezium connectors stop,
     * publishers flush/close, and offsets are persisted before exit. Without
     * this, a pod termination left connections open and position unpersisted.
     */
    @PreDestroy
    public void onShutdown() {
        shutdownAllGlobal(GlobalShutdown.CONFIRMED);
    }

    private void shutdownOne(CaptureEntry entry) {
        if (entry.connector() != null) {
            var offset = entry.connector().currentOffset();
            if (!offset.isEmpty())
                // D2: scope the offset key by tenant so two tenants with the same
                // pipelineId do not overwrite each other's resume position.
                offsetStore.save(tenantKey(entry.tenantId(), entry.pipelineId()), offset);
            entry.connector().stopCDC();
        }
        try {
            entry.publisher().flush();
        } catch (Exception ignored) {
        }
        try {
            entry.publisher().close();
        } catch (Exception ignored) {
        }
        kafkaCdcConsumer.ifPresent(c -> c.stopConsuming(entry.pipelineId()));
    }

    /** Explicit-op confirmation token for tenant-blind shutdownAllGlobal. */
    public enum GlobalShutdown {
        CONFIRMED
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Task #7 bridge: when Kafka is enabled, provision topics and return a
     * KafkaEventPublisher; otherwise fall back to BoundedQueueEventPublisher
     * wrapped in a CircuitBreakerEventPublisher.
     *
     * <p>
     * The circuit breaker guards the publish() call path. When the downstream
     * write path (SyncOrchestrator → DestinationRouter) is failing, the circuit
     * opens and new CDC events are rejected fast rather than piling up in the
     * bounded queue, giving the destination time to recover without OOM risk.
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
        // Bounded queue wrapped in a Resilience4j circuit breaker (F7).
        // The circuit opens on ≥ 50 % failures in a 10-event window, staying open
        // for 30 s. While open, publish() calls are rejected immediately (counted
        // in metrics via the CB event listener) rather than blocking the Debezium
        // engine thread.
        var inner = new BoundedQueueEventPublisher();
        var cb = new CircuitBreakerEventPublisher(pipelineId, inner);
        // Bind this pipeline's circuit breaker metrics to the shared Micrometer
        // registry. Uses the CB's Micrometer event publisher so state changes
        // (OPEN/HALF_OPEN/CLOSED) appear as gauge metrics in Prometheus/Grafana.
        cb.circuitBreaker().getEventPublisher()
                .onStateTransition(e -> meterRegistry.counter(
                        "syncflow.cdc.circuit_breaker.transitions",
                        "pipeline", pipelineId,
                        "from", e.getStateTransition().getFromState().name(),
                        "to", e.getStateTransition().getToState().name()).increment());
        log.info("Using CircuitBreakerEventPublisher(BoundedQueue) for pipeline={} (Kafka disabled)",
                pipelineId);
        return cb;
    }

    /**
     * Persist (or update) the active-capture record in Postgres. The in-memory
     * map remains the fast path for in-process reads; the DB row is the
     * recovery path on restart.
     */
    @org.springframework.transaction.annotation.Transactional
    void persistActiveCapture(String key, String tenantId, String pipelineId,
            CaptureStatus status, Map<String, String> offset) {
        var entity = activeCaptureRepo.findById(key).orElseGet(() -> {
            var e = new ActiveCaptureEntity();
            e.setId(key);
            e.setTenantId(tenantId);
            e.setPipelineId(pipelineId);
            e.setStartedAt(Instant.now());
            return e;
        });
        entity.setStatus(status);
        try {
            entity.setOffsetData(objectMapper.writeValueAsString(offset));
        } catch (Exception e) {
            log.warn("Failed to serialize offset for pipeline={}", pipelineId, e);
            entity.setOffsetData("{}");
        }
        entity.setUpdatedAt(Instant.now());
        activeCaptureRepo.save(entity);
    }

    /**
     * Rehydrate an in-memory capture entry from the durable record. Called
     * on pod startup to restore active captures before traffic resumes.
     */
    public int rehydrateFromDatabase() {
        var count = 0;
        for (var entity : activeCaptureRepo.findAll()) {
            if (entity.getStatus() == CaptureStatus.RUNNING) {
                // The actual connector / publisher re-instantiation is the
                // caller's job (see CaptureRehydrationService). Here we just
                // make sure the entity is loaded.
                count++;
            }
        }
        return count;
    }

    private record CaptureEntry(CdcCapableConnector connector, EventPublisher publisher, String pipelineId,
            String tenantId) {
    }
}
