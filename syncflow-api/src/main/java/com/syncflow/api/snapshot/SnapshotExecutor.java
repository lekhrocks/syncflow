package com.syncflow.api.snapshot;

import com.syncflow.api.config.RuntimeProperties;
import com.syncflow.api.connection.ConnectionMapper;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.runtimestate.RuntimeStateJson;
import com.syncflow.api.snapshot.entity.SnapshotJobEntity;
import com.syncflow.api.snapshot.repository.SnapshotJobRepository;
import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.snapshot.SnapshotCheckpoint;
import com.syncflow.core.snapshot.SnapshotError;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import com.syncflow.core.snapshot.SnapshotStatistics;
import com.syncflow.core.snapshot.pipeline.FilterProcessor;
import com.syncflow.core.snapshot.pipeline.ProcessingContext;
import com.syncflow.core.snapshot.pipeline.TransformProcessor;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.SnapshotCapableConnector;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.core.spi.writer.WriterRegistry;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SnapshotExecutor {

    private final PipelineDesignerService pipelineService;
    private final ConnectionService connectionService;
    private final ConnectorRegistry connectorRegistry;
    private final WriterRegistry writerRegistry;
    private final CheckpointStore checkpointStore;
    private final SnapshotJobRepository jobRepository;
    private final RuntimeStateJson json;
    private final MeterRegistry meterRegistry;
    private final StatusBroadcaster broadcaster;
    private final RuntimeProperties runtime;

    // In-memory worker state: cancellation flags + tenant ownership. The job
    // payload itself is durable in snapshot_jobs; the in-memory job cache is a
    // fast-path read (writes round-trip to Postgres on every state change).
    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();
    private final Map<String, String> tenantOf = new ConcurrentHashMap<>();

    public SnapshotExecutor(PipelineDesignerService pipelineService,
            ConnectionService connectionService,
            ConnectorRegistry connectorRegistry,
            WriterRegistry writerRegistry,
            CheckpointStore checkpointStore,
            SnapshotJobRepository jobRepository,
            RuntimeStateJson json,
            MeterRegistry meterRegistry,
            StatusBroadcaster broadcaster,
            RuntimeProperties runtime) {
        this.pipelineService = pipelineService;
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.writerRegistry = writerRegistry;
        this.checkpointStore = checkpointStore;
        this.jobRepository = jobRepository;
        this.json = json;
        this.meterRegistry = meterRegistry;
        this.broadcaster = broadcaster;
        this.runtime = runtime;
    }

    public SnapshotJob start(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var pipeline = pipelineService.get(pipelineId);
        var job = new SnapshotJob(pipelineId).withRunning();
        var snapshotId = job.getId().value();
        persist(job, tenantContext);
        cancellations.put(snapshotId, new AtomicBoolean(false));

        tenantOf.put(snapshotId, tenantContext.tenantId().value());
        Thread.startVirtualThread(() -> execute(tenantContext, job, pipeline));
        return job;
    }

    @Transactional(readOnly = true)
    public SnapshotJob get(String snapshotId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        return Optional.ofNullable(findOwned(snapshotId, tenantContext))
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Snapshot not found: " + snapshotId));
    }

    /** Only the current tenant's snapshots. */
    @Transactional(readOnly = true)
    public List<SnapshotJob> list(TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        return jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId().value()).stream()
                .map(this::toDomain)
                .toList();
    }

    public SnapshotJob cancel(String snapshotId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var flag = cancellations.get(snapshotId);
        if (flag != null)
            flag.set(true);
        var job = Optional.ofNullable(findOwned(snapshotId, tenantContext))
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Snapshot not found: " + snapshotId));
        var cancelled = job.withCancelled();
        persist(cancelled, tenantContext);
        // A cancelled snapshot is terminal; release its in-memory state.
        remove(snapshotId);
        return cancelled;
    }

    /**
     * Release in-memory worker state for a terminal snapshot (job stays durable).
     */
    private void remove(String snapshotId) {
        cancellations.remove(snapshotId);
        tenantOf.remove(snapshotId);
    }

    private void execute(TenantContext tenantContext, SnapshotJob job, PipelineDesign pipeline) {
        // DEEP FIX: TenantContext is threaded explicitly through every call;
        // we do not set the ThreadLocal. See SyncOrchestrator.run() for the
        // same pattern.
        try {
            executeInner(job, pipeline, tenantContext);
        } finally {
            // Defensive cleanup of any stale ThreadLocal.
            TenantContextHolder.clear();
        }
    }

    private void executeInner(SnapshotJob job, PipelineDesign pipeline, TenantContext tenantContext) {
        var timer = Timer.builder("syncflow.snapshot.duration")
                .tag("pipeline", pipeline.id().value())
                .register(meterRegistry);
        var sample = Timer.start(meterRegistry);
        var rowsProcessed = new AtomicLong(0);
        var batchesDone = new AtomicLong(0);

        DestinationWriter writer = null;
        try {
            var sourceCtx = buildSourceContext(pipeline);
            var destCfg = buildDestConfig(pipeline);
            var connector = resolveSourceConnector(pipeline);
            writer = resolveWriter(pipeline);

            writer.connect(destCfg);

            long totalRows = 0;
            long totalBatches = 0;
            // Aggregate row/batch estimates across ALL mapped tables so progress %
            // is meaningful for multi-table pipelines (not just the first mapping).
            for (var tm : pipeline.tableMappings()) {
                var tableRows = connector.estimateRows(sourceCtx, pipeline.source().schema(), tm.sourceTable());
                totalRows += tableRows;
                totalBatches += (tableRows / pipeline.settings().batchSize()) + 1;
            }

            var progress = SnapshotProgress.starting(totalRows);
            persist(job.withProgress(progress), tenantContext);

            for (var tm : pipeline.tableMappings()) {
                if (isCancelled(job))
                    break;
                var ctx = new ProcessingContext(pipeline, tm);

                var checkpoint = checkpointStore.get(pipeline.id().value(), tm.sourceTable());
                // Resume from the last checkpointed cursor; else start fresh.
                String cursor = (checkpoint != null) ? checkpoint.cursor() : null;
                int batchNumber = (checkpoint != null) ? checkpoint.lastBatchNumber() + 1 : 0;

                var batchInfo = new BatchInformation(batchNumber, pipeline.settings().batchSize(),
                        tm.sourceTable(), cursor);
                var page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                        tm.sourceTable(), batchInfo);

                var chain = new FilterProcessor().andThen(new TransformProcessor());

                while (page != null && !page.rows().isEmpty() && !isCancelled(job)) {
                    var batch = page.rows().stream()
                            .map(r -> chain.process(r, ctx))
                            .filter(Objects::nonNull)
                            .toList();

                    if (!batch.isEmpty()) {
                        var destCols = tm.columnMappings().stream()
                                .map(ColumnMapping::destinationColumn)
                                .toList();
                        writer.writeBatch(tm.destinationTable() != null
                                ? tm.destinationTable()
                                : tm.destinationCollection(),
                                destCols, batch);
                    }

                    rowsProcessed.addAndGet(batch.size());
                    batchesDone.incrementAndGet();
                    var pct = totalRows > 0 ? (double) rowsProcessed.get() / totalRows * 100 : 0;
                    var updated = job.withProgress(new SnapshotProgress(
                            (int) batchesDone.get(), (int) totalBatches,
                            rowsProcessed.get(), totalRows, pct, 0));
                    persist(updated, tenantContext);
                    emit(job.getId().value(), updated, tenantContext);

                    meterRegistry.counter("syncflow.snapshot.rows",
                            "pipeline", pipeline.id().value()).increment(batch.size());

                    // Checkpoint every N batches (configurable) — captures the keyed cursor so a
                    // resume continues exactly at the next row (no OFFSET drift).
                    if (batchesDone.get() % runtime.getSnapshot().getCheckpointIntervalBatches() == 0) {
                        checkpointStore.save(new SnapshotCheckpoint(
                                pipeline.id().value(), tm.sourceTable(),
                                (int) batchesDone.get(), rowsProcessed.get(),
                                page.nextCursor()));
                    }

                    // Next read continues from this page's cursor.
                    var nextBatchInfo = new BatchInformation(
                            (int) batchesDone.get(), pipeline.settings().batchSize(),
                            tm.sourceTable(), page.nextCursor());
                    page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                            tm.sourceTable(), nextBatchInfo);
                }
            }

            if (isCancelled(job)) {
                // Do not commit partial writes on cancel — a later resume would
                // duplicate the already-written rows.
                writer.rollback();
            } else {
                writer.flush();
                writer.commit();
            }

            var elapsed = sample.stop(timer);
            if (!isCancelled(job)) {
                var stats = new SnapshotStatistics(totalRows, rowsProcessed.get(),
                        batchesDone.get(), totalBatches, 0, 0,
                        job.getCreatedAt(), Instant.now(), elapsed / 1_000_000);
                var completed = job.withCompleted(stats);
                persist(completed, tenantContext);
                emit(job.getId().value(), completed, tenantContext);
                checkpointStore.deleteAll(pipeline.id().value());
                // Terminal and durable; release worker state so the in-memory
                // maps cannot grow unbounded across snapshots.
                remove(job.getId().value());
            }
        } catch (Exception e) {
            sample.stop(timer);
            if (writer != null) {
                try {
                    writer.rollback();
                } catch (Exception ignored) {
                }
            }
            var error = new SnapshotError("SNAPSHOT_FAILED", e.getMessage(),
                    (int) batchesDone.get(), Instant.now());
            var failed = job.withFailed(List.of(error));
            persist(failed, tenantContext);
            emit(job.getId().value(), failed, tenantContext);
            remove(job.getId().value());
            meterRegistry.counter("syncflow.snapshot.errors",
                    "pipeline", pipeline.id().value()).increment();
        }
    }

    /** Live-status event emitted on every progress/state change for a snapshot. */
    private void emit(String snapshotId, SnapshotJob job, TenantContext tenantContext) {
        // Tenant-scoped SSE key (matching the tenantOf map) so streams don't cross.
        var key = tenantContext.tenantId().value() + ":" + snapshotId;
        broadcaster.emit(key, "snapshot-status", job);
    }

    private boolean isCancelled(SnapshotJob job) {
        var flag = cancellations.get(job.getId().value());
        return flag != null && flag.get();
    }

    private SnapshotJobEntity findOwned(String snapshotId, TenantContext tenantContext) {
        var tenant = tenantContext.tenantId().value();
        return jobRepository.findById(snapshotId)
                .filter(e -> tenant.equals(e.getTenantId()))
                .orElse(null);
    }

    @Transactional
    private void persist(SnapshotJob job, TenantContext tenantContext) {
        var entity = jobRepository.findById(job.getId().value())
                .orElseGet(SnapshotJobEntity::new);
        entity.setId(job.getId().value());
        entity.setTenantId(tenantContext.tenantId().value());
        entity.setPipelineId(job.getPipelineId());
        entity.setStatus(job.getStatus().name());
        entity.setPayload(json.toJson(job));
        entity.setCreatedAt(job.getCreatedAt());
        entity.setUpdatedAt(Instant.now());
        jobRepository.save(entity);
    }

    private SnapshotJob toDomain(SnapshotJobEntity e) {
        return json.fromJson(e.getPayload(), SnapshotJob.class);
    }

    private ConnectorContext buildSourceContext(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.source().connectionId());
        var config = ConnectionMapper.toConfig(conn);
        return new ConnectorContext(config, Map.of());
    }

    private ConnectionConfiguration buildDestConfig(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.destination().connectionId());
        return ConnectionMapper.toConfig(conn);
    }

    private SnapshotCapableConnector resolveSourceConnector(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.source().connectionId());
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var c = connectorRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No connector for type: " + ct));
        if (!(c instanceof SnapshotCapableConnector sc)) {
            throw new IllegalArgumentException("Connector does not support snapshot: " + ct);
        }
        return sc;
    }

    private DestinationWriter resolveWriter(PipelineDesign pipeline) {
        var conn = connectionService.getWithDecryptedCredentials(pipeline.destination().connectionId());
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        return writerRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No writer for type: " + ct));
    }

}
