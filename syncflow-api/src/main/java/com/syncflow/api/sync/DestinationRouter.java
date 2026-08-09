package com.syncflow.api.sync;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.core.spi.writer.WriterRegistry;
import com.syncflow.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routes CDC events to the destination via the registered writers.
 * <p>
 * A single writer + JDBC connection is kept open per pipeline (keyed by
 * {@code tenant:pipeline}) and reused across events. Events accumulate in the
 * writer's batch buffer and are flushed + committed once the batch reaches
 * {@link #COMMIT_BATCH} events or {@link #COMMIT_INTERVAL_MS} elapses — not on
 * every event. This removes the previous connect/commit/close-per-event churn
 * that dominated the hot path.
 * <p>
 * The orchestrator calls {@link #closePipeline(String)} when a sync run stops so
 * pending batches are flushed and the connection released. Any pipeline not
 * closed is flushed by the periodic sweeper.
 */
@Component
public class DestinationRouter {

    private static final Logger log = LoggerFactory.getLogger(DestinationRouter.class);

    /** Commit after this many buffered events per pipeline. */
    static final int COMMIT_BATCH = 500;
    /** Commit if the pipeline's buffer has been idle this long. */
    static final long COMMIT_INTERVAL_MS = 5_000;
    /** Timeout after which an idle open connection is closed. */
    static final long IDLE_TIMEOUT_MS = 60_000;

    private final WriterRegistry writerRegistry;
    private final ConnectionService connectionService;

    /** Active writer + connection per pipeline; keyed {@code tenant:pipeline}. */
    private final Map<String, ActiveWriter> active = new ConcurrentHashMap<>();

    public DestinationRouter(WriterRegistry writerRegistry,
            ConnectionService connectionService) {
        this.writerRegistry = writerRegistry;
        this.connectionService = connectionService;
    }

    /**
     * Write a single CDC event to the pipeline's destination. Buffers the row
     * into the active writer and commits periodically (batch size or time based).
     */
    public WriteResult write(String pipelineId, CDCEvent event, List<String> destColumns) {
        var key = tenantKey(pipelineId);
        try {
            var activeWriter = active.computeIfAbsent(key, k -> open(pipelineId, event));
            var tableName = event.source().table();
            switch (event.operation()) {
                case INSERT, UPDATE -> {
                    if (event.payload().after() != null) {
                        activeWriter.writer().writeBatch(tableName,
                                List.of(event.payload().after()), destColumns);
                    }
                }
                case DELETE -> {
                    // Deletes are routed to the DLQ via the caller when the writer
                    // cannot represent tombstones; a no-op write is not acceptable
                    // (silent data loss). See SyncOrchestrator.processEvent.
                }
                default -> {
                }
            }
            activeWriter.events().incrementAndGet();
            maybeCommit(activeWriter);
            return new WriteResult(true, null);
        } catch (Exception e) {
            var activeWriter = active.get(key);
            if (activeWriter != null) {
                safeRollback(activeWriter.writer());
            }
            return new WriteResult(false, e.getMessage());
        }
    }

    /** Flush + commit the pipeline's buffered rows and release its connection. */
    public void closePipeline(String pipelineId) {
        var removed = active.remove(tenantKey(pipelineId));
        if (removed == null)
            return;
        try {
            removed.writer().flush();
            removed.writer().commit();
        } catch (Exception e) {
            safeRollback(removed.writer());
        } finally {
            try {
                removed.writer().close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Close any pipelines that have been idle too long (periodic sweeper). */
    public void sweepIdle() {
        var now = System.currentTimeMillis();
        active.forEach((key, aw) -> {
            if (now - aw.lastActivity() > IDLE_TIMEOUT_MS) {
                var pipelineId = key.substring(key.indexOf(':') + 1);
                closePipeline(pipelineId);
            }
        });
    }

    private ActiveWriter open(String pipelineId, CDCEvent event) {
        var conn = connectionService.getWithDecryptedCredentials(event.header().connectionId());
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var writer = writerRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No writer for: " + ct));
        writer.connect(toConfig(conn));
        log.debug("Opened destination writer for pipeline={} type={}", pipelineId, ct);
        return new ActiveWriter(writer);
    }

    private void maybeCommit(ActiveWriter aw) {
        var now = System.currentTimeMillis();
        if (aw.events().get() >= COMMIT_BATCH || now - aw.lastActivity() > COMMIT_INTERVAL_MS) {
            try {
                aw.writer().flush();
                aw.writer().commit();
                aw.events().set(0);
            } catch (Exception e) {
                // Commit failure rolls back the batch; the caller routes to DLQ on
                // the next write failure. Surface it here so it is never silent.
                log.warn("Destination commit failed for pipeline (will rollback): {}", e.getMessage());
                safeRollback(aw.writer());
                aw.events().set(0);
            }
        }
        aw.lastActivity(now);
    }

    private void safeRollback(DestinationWriter writer) {
        try {
            writer.rollback();
        } catch (Exception ignored) {
        }
    }

    /** Tenant-scoped key so one tenant's writer cannot collide with another's. */
    private static String tenantKey(String pipelineId) {
        return TenantContextHolder.getTenantId().value() + ":" + pipelineId;
    }

    private ConnectionConfiguration toConfig(Connection conn) {
        var p = conn.getProperties();
        var c = conn.getCredentials();
        return new ConnectionConfiguration(
                ConnectorTypeMapper.toCore(p.type()),
                p.host(), p.port(), p.database(),
                c.username(), c.password(), p.options());
    }

    public record WriteResult(boolean success, String error) {
    }

    /** Per-pipeline writer + connection with event/activity bookkeeping. */
    private static final class ActiveWriter {

        private final DestinationWriter writer;
        private final AtomicLong events = new AtomicLong(0);
        private volatile long lastActivity = System.currentTimeMillis();

        ActiveWriter(DestinationWriter writer) {
            this.writer = writer;
        }

        DestinationWriter writer() {
            return writer;
        }

        AtomicLong events() {
            return events;
        }

        long lastActivity() {
            return lastActivity;
        }

        void lastActivity(long v) {
            this.lastActivity = v;
        }
    }
}
