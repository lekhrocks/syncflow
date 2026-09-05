package com.syncflow.api.snapshot;

import com.syncflow.api.config.MetricsHelper;
import com.syncflow.api.config.RuntimeProperties;
import com.syncflow.api.connection.ConnectionMapper;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.lock.DistributedLockService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.runtimestate.RuntimeStateJson;
import com.syncflow.persistence.snapshot.entity.SnapshotJobEntity;
import com.syncflow.persistence.snapshot.repository.SnapshotJobRepository;
import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.snapshot.ChunkRange;
import com.syncflow.core.snapshot.SnapshotError;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import com.syncflow.core.snapshot.SnapshotStatus;
import com.syncflow.core.snapshot.SnapshotStatistics;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SnapshotExecutor {

    private final PipelineDesignerService pipelineService;
    private final ConnectionService connectionService;
    private final DistributedLockService lockService;
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
            DistributedLockService lockService,
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
        this.lockService = lockService;
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
        return lockService.withLock("snapshot:" + pipelineId, tenantContext,
                Duration.ofSeconds(30), () -> doStart(pipelineId, tenantContext));
    }

    /**
     * Race-safe start behind the distributed lock: two pods cannot start the
     * same pipeline's snapshot concurrently (S2). If a RUNNING job already
     * exists for this tenant+pipeline, return it instead of spawning a second
     * worker (mirrors {@code SyncOrchestrator.start()}).
     */
    private SnapshotJob doStart(String pipelineId, TenantContext tenantContext) {
        // A RUNNING snapshot only short-circuits start when this JVM actually
        // owns a live worker for it (the existing RUNNING row's snapshotId is
        // in `cancellations` and not flagged cancelled). A stale RUNNING row
        // left by a crashed pod has no entry — treat it as startable, not as
        // "already running", so snapshots can't get stuck RUNNING forever.
        var tenantId = tenantContext.tenantId().value();
        var existing = jobRepository
                .findByTenantIdAndPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId)
                .stream().findFirst().map(this::toDomain).orElse(null);
        if (existing != null && existing.getStatus() == SnapshotStatus.RUNNING) {
            var flag = cancellations.get(existing.getId().value());
            if (flag != null && !flag.get()) {
                return existing;
            }
        }
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
        var job = Optional.ofNullable(findOwned(snapshotId, tenantContext))
                .map(this::toDomain)
                .orElseThrow(() -> new NoSuchElementException("Snapshot not found: " + snapshotId));
        // A cancel is only meaningful for a job that is still running. If the
        // worker already finished (COMPLETED) or the job is otherwise terminal,
        // cancel must not overwrite that status — the caller was too late.
        if (job.getStatus() == SnapshotStatus.COMPLETED
                || job.getStatus() == SnapshotStatus.FAILED
                || job.getStatus() == SnapshotStatus.CANCELLED) {
            return job;
        }
        var cancelled = job.withCancelled();
        // Serialize the cancel flag-set and the CANCELLED persist with the
        // worker's terminal COMPLETED persist (both take progressLock), so the
        // two terminal states cannot race: a cancel that lands mid-terminal wins
        // BEFORE the worker commits, instead of overriding the status after the
        // commit. The worker re-checks isCancelled() inside the same lock.
        synchronized (progressLock) {
            if (flag != null)
                flag.set(true);
            persist(cancelled, tenantContext);
        }
        // Do NOT release the in-memory cancel flag here. The worker must still
        // observe it at its terminal check to keep the CANCELLED status from
        // being overridden by a COMPLETED commit; the worker clears it when it
        // finishes (executeInner's terminal branches call remove()).
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
        // DestinationWriter is single-connection and not thread-safe; writes
        // across parallel chunk workers serialize on this monitor.
        var writerLock = new Object();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        DestinationWriter writer = null;
        try {
            var sourceCtx = buildSourceContext(pipeline);
            var destCfg = buildDestConfig(pipeline);
            var connector = resolveSourceConnector(pipeline);
            writer = resolveWriter(pipeline);
            final DestinationWriter sharedWriter = writer;

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
            final long finalTotalRows = totalRows;
            final long finalTotalBatches = totalBatches;

            var parallelism = runtime.getSnapshot().getParallelism();
            var maxChunks = runtime.getSnapshot().getMaxChunks();
            // One task per (table, chunk-range) work item. Tables without a
            // numeric single-column PK yield one whole-table chunk (sequential).
            var workItems = new ArrayList<WorkItem>();
            for (var tm : pipeline.tableMappings()) {
                var ranges = connector.rangeChunks(sourceCtx, pipeline.source().schema(),
                        tm.sourceTable(), maxChunks);
                for (var range : ranges) {
                    workItems.add(new WorkItem(tm, range));
                }
            }

            var poolSize = Math.max(1, Math.min(parallelism, workItems.size()));
            // Each worker thread owns ONE exclusive connector clone for its whole
            // lifetime and drains a shared work queue, so no two in-flight tasks
            // ever share a JDBC Connection (which is not thread-safe). A 64-chunk
            // table with 4 workers still opens only 4 DB connections.
            var workQueue = new java.util.concurrent.LinkedBlockingQueue<WorkItem>(workItems);
            var workerClones = new ArrayList<SnapshotCapableConnector>();
            try {
                for (int i = 0; i < poolSize; i++) {
                    workerClones.add(connector.snapshotClone(sourceCtx));
                }
                var workers = new ArrayList<Thread>(poolSize);
                for (var workerConnector : workerClones) {
                    workers.add(Thread.startVirtualThread(() -> {
                        while (true) {
                            var wi = workQueue.poll();
                            if (wi == null) {
                                break; // the queue is drained
                            }
                            try {
                                snapshotRange(job, pipeline, tenantContext, workerConnector,
                                        sharedWriter, writerLock, wi.table(), wi.range(), sourceCtx,
                                        rowsProcessed, batchesDone, finalTotalRows, finalTotalBatches);
                            } catch (Throwable t) {
                                failure.compareAndSet(null, t);
                            }
                        }
                    }));
                }
                for (var worker : workers) {
                    worker.join();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                for (var clone : workerClones) {
                    try {
                        clone.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (failure.get() != null) {
                throw new RuntimeException("Snapshot range failed", failure.get());
            }

            var elapsed = sample.stop(timer);
            // Decide the terminal state under progressLock so it cannot race the
            // cancel() path. The in-memory flag is re-checked inside the lock:
            // if cancel() ran between the loop's check and here, the snapshot is
            // CANCELLED and must not be overridden by COMPLETED.
            synchronized (progressLock) {
                if (isCancelled(job)) {
                    // Do not commit partial writes on cancel — a later resume would
                    // duplicate the already-written rows.
                    writer.rollback();
                } else {
                    writer.flush();
                    writer.commit();
                    var stats = new SnapshotStatistics(totalRows, rowsProcessed.get(),
                            batchesDone.get(), totalBatches, 0, 0,
                            job.getCreatedAt(), Instant.now(), elapsed / 1_000_000);
                    var completed = job.withCompleted(stats);
                    persist(completed, tenantContext);
                    emit(job.getId().value(), completed, tenantContext);
                    checkpointStore.deleteAll(tenantContext.tenantId().value(), pipeline.id().value());
                }
                // Release worker state once the worker has decided its terminal
                // state (COMPLETED or CANCELLED stood).
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
            // A cancellation outranks a failure — the user asked to stop, so the
            // CANCELLED status (already persisted by cancel()) must not be
            // overwritten by FAILED. Same lock discipline as the completion path.
            synchronized (progressLock) {
                if (!isCancelled(job)) {
                    var failed = job.withFailed(List.of(error));
                    persist(failed, tenantContext);
                    emit(job.getId().value(), failed, tenantContext);
                }
                remove(job.getId().value());
            }
            MetricsHelper.increment(meterRegistry, "syncflow.snapshot.errors",
                    "pipeline", pipeline.id().value());
        }
    }

    /**
     * Snapshot one PK-range chunk of one table: delegates to
     * {@link SnapshotWorker} for the actual read/transform/write loop.
     */
    private void snapshotRange(SnapshotJob job, PipelineDesign pipeline, TenantContext tenantContext,
            SnapshotCapableConnector connector, DestinationWriter writer, Object writerLock,
            TableMapping tm, ChunkRange range, ConnectorContext sourceCtx,
            AtomicLong rowsProcessed, AtomicLong batchesDone, long totalRows, long totalBatches) {
        SnapshotWorker.snapshotRange(job, pipeline, tenantContext,
                connector, writer, writerLock, tm, range, sourceCtx,
                rowsProcessed, batchesDone, totalRows, totalBatches,
                checkpointStore, runtime, progressLock,
                () -> isCancelled(job),
                (updated, ctx) -> persist(updated, ctx),
                (id, updated, ctx) -> emit(id, updated, ctx),
                meterRegistry);
    }

    /**
     * Aggregate live-progress publication across parallel chunk workers is
     * serialized on this monitor; {@link #persist} reads the whole job payload
     * and writes it back, so two workers persisting concurrently would clobber
     * each other's progress.
     */
    private final Object progressLock = new Object();

    /** A (table mapping, chunk range) work item for the parallel snapshot. */
    private record WorkItem(TableMapping table, ChunkRange range) {
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
