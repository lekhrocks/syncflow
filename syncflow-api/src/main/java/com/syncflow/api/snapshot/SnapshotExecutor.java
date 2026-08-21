package com.syncflow.api.snapshot;

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
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.snapshot.ChunkRange;
import com.syncflow.core.snapshot.SnapshotCheckpoint;
import com.syncflow.core.snapshot.SnapshotError;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import com.syncflow.core.snapshot.SnapshotStatus;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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
            meterRegistry.counter("syncflow.snapshot.errors",
                    "pipeline", pipeline.id().value()).increment();
        }
    }

    /**
     * Snapshot one PK-range chunk of one table: keyset-paginate the range,
     * filter/transform, and batch-write to the destination. Writes serialize on
     * {@code writerLock}; read/transform run in parallel across chunk workers.
     * Resume continues from the chunk's own checkpoint cursor.
     */
    private void snapshotRange(SnapshotJob job, PipelineDesign pipeline, TenantContext tenantContext,
            SnapshotCapableConnector connector, DestinationWriter writer, Object writerLock,
            TableMapping tm, ChunkRange range, ConnectorContext sourceCtx,
            AtomicLong rowsProcessed, AtomicLong batchesDone, long totalRows, long totalBatches) {
        var ctx = new ProcessingContext(pipeline, tm);
        var checkpoint = checkpointStore.get(
                tenantContext.tenantId().value(), pipeline.id().value(), tm.sourceTable(), range.index());
        // Resume only from a checkpoint cursor that still lies inside this
        // chunk's bounds. A legacy whole-table checkpoint shares chunk_index=0
        // with chunk 0 — resuming from its (potentially out-of-range) cursor
        // would silently skip rows in [start, cursor). Out-of-range → start the
        // chunk fresh.
        String cursor = (checkpoint != null && cursorWithinRange(checkpoint.cursor(), range))
                ? checkpoint.cursor()
                : null;
        int batchNumber = (checkpoint != null) ? checkpoint.lastBatchNumber() + 1 : 0;
        // Per-chunk batch counter for checkpoint/progress cadence. It is local
        // to this worker (resumed from the chunk's checkpoint) so cadence is
        // accurate per chunk rather than diluted across parallel workers sharing
        // the global counter. It starts one below the FIRST read's ordinal:
        // each increment yields the ordinal of the page just read (fresh: 0,1,2…
        // resume: L+1, L+2…), and the next page is chunkBatch + 1 — this is what
        // makes offset-based connectors paginate correctly on resume.
        var chunkBatchCounter = new AtomicLong(batchNumber - 1);

        // Destination columns, table, and upsert keys are constant for the whole
        // chunk — derive once and reuse across every page instead of rebuilding
        // them per batch.
        var destCols = tm.columnMappings().stream()
                .map(ColumnMapping::destinationColumn)
                .toList();
        var destTable = tm.destinationTable() != null
                ? tm.destinationTable()
                : tm.destinationCollection();
        var keyCols = tm.primaryKey() != null ? tm.primaryKey().destinationColumns() : null;
        var useUpsert = keyCols != null && !keyCols.isEmpty();
        var chain = new FilterProcessor().andThen(new TransformProcessor());

        // Do not read the first page at all if the snapshot was already
        // cancelled — otherwise a cancel landing before the loop-top check would
        // still write (and auto-commit) this chunk's first batch.
        if (isCancelled(job)) {
            return;
        }
        var batchInfo = new BatchInformation(batchNumber, pipeline.settings().batchSize(),
                tm.sourceTable(), cursor, range);
        var page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                tm.sourceTable(), batchInfo);

        while (page != null && !page.rows().isEmpty() && !isCancelled(job)) {
            var batch = page.rows().stream()
                    .map(r -> chain.process(r, ctx))
                    .filter(Objects::nonNull)
                    .toList();

            if (!batch.isEmpty()) {
                synchronized (writerLock) {
                    // Upsert when a destination PK is mapped so a resume that
                    // re-reads already-committed rows (the pooled writer
                    // auto-commits each batch; there is no transaction to roll
                    // back) is idempotent instead of inserting duplicates or
                    // tripping a constraint violation.
                    if (useUpsert) {
                        writer.upsertBatch(destTable, destCols, batch, keyCols);
                    } else {
                        writer.writeBatch(destTable, destCols, batch);
                    }
                }
            }

            rowsProcessed.addAndGet(batch.size());
            batchesDone.incrementAndGet();
            // Per-chunk batch counter for checkpoint cadence — the shared global
            // counter would spread checkpoints unevenly across parallel workers.
            var chunkBatch = chunkBatchCounter.incrementAndGet();
            meterRegistry.counter("syncflow.snapshot.rows",
                    "pipeline", pipeline.id().value()).increment(batch.size());

            // Checkpoint every N batches (configurable) — captures the chunk's
            // keyed cursor so a resume continues exactly at the next row.
            if (chunkBatch % runtime.getSnapshot().getCheckpointIntervalBatches() == 0) {
                checkpointStore.save(tenantContext.tenantId().value(), new SnapshotCheckpoint(
                        pipeline.id().value(), tm.sourceTable(), range.index(),
                        (int) chunkBatch, rowsProcessed.get(), page.nextCursor()));
            }

            // Publish live progress every N batches, serialized so the shared
            // (job, progress) read-modify-write cannot lose updates across
            // parallel workers.
            synchronized (progressLock) {
                // Re-check cancellation under the lock before publishing. A
                // cancel() that landed after the loop-top check persists
                // CANCELLED under this same monitor; publishing progress here
                // would write a RUNNING-status job over it and strand a zombie
                // RUNNING row with no live worker.
                if (!isCancelled(job)
                        && chunkBatch % runtime.getSnapshot().getProgressPublishIntervalBatches() == 0) {
                    var pct = totalRows > 0 ? (double) rowsProcessed.get() / totalRows * 100 : 0;
                    var updated = job.withProgress(new SnapshotProgress(
                            (int) batchesDone.get(), (int) totalBatches,
                            rowsProcessed.get(), totalRows, pct, 0));
                    persist(updated, tenantContext);
                    emit(job.getId().value(), updated, tenantContext);
                }
            }

            // Next read continues from this page's cursor within this chunk. Its
            // batchNumber is the ordinal of the NEXT batch: this page was
            // chunkBatch (after the increment above), so the next is
            // chunkBatch + 1. Keeping the ordinal per-chunk (not the shared
            // global counter) means offset-based connectors (Mongo, PK-less
            // JDBC) paginate by batchNumber * batchSize without skipping or
            // re-reading rows.
            var nextBatchInfo = new BatchInformation(
                    (int) chunkBatch + 1, pipeline.settings().batchSize(),
                    tm.sourceTable(), page.nextCursor(), range);
            page = connector.readBatch(sourceCtx, pipeline.source().schema(),
                    tm.sourceTable(), nextBatchInfo);
        }
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

    /**
     * A resume cursor is meaningful for a chunk only if it lies within
     * {@code [start, end)}. Numeric cursors/bounds compare by value; anything
     * non-numeric (uuid/text) or out of range is not a valid resume point.
     */
    private static boolean cursorWithinRange(String cursor, ChunkRange range) {
        if (cursor == null || range == null) {
            return false;
        }
        if (range.start() instanceof Number start) {
            try {
                BigDecimal c = new BigDecimal(cursor);
                BigDecimal lo = new BigDecimal(start.toString());
                // Start-inclusive. The end bound, when present, is exclusive;
                // a null end (open-ended last chunk / whole table) accepts any
                // cursor at or after start.
                if (c.compareTo(lo) < 0) {
                    return false;
                }
                return range.end() == null
                        || !(range.end() instanceof Number end)
                        || c.compareTo(new BigDecimal(end.toString())) < 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        // Whole-table (unbounded start) ranges: any cursor is valid.
        return range.start() == null;
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
