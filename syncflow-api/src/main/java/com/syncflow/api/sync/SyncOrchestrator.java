package com.syncflow.api.sync;

import com.syncflow.api.cdc.CaptureLifecycle;
import com.syncflow.api.config.MetricsHelper;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.runtimestate.RuntimeStateJson;
import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.persistence.sync.entity.SyncJobEntity;
import com.syncflow.persistence.sync.repository.SyncJobRepository;
import com.syncflow.persistence.sync.repository.EventQueueSnapshotRepository;
import com.syncflow.persistence.sync.entity.EventQueueSnapshotEntity;
import com.syncflow.api.config.RuntimeProperties;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.snapshot.pipeline.FilterProcessor;
import com.syncflow.core.snapshot.pipeline.ProcessingContext;
import com.syncflow.core.snapshot.pipeline.TransformProcessor;
import com.syncflow.core.sync.FailureReason;
import com.syncflow.core.sync.SyncJob;
import com.syncflow.core.sync.SyncState;
import com.syncflow.core.sync.SyncStatistics;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class SyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncOrchestrator.class);

    private final CaptureLifecycle captureLifecycle;
    private final PipelineDesignerService pipelineService;
    private final DestinationRouter router;
    private final EventIdempotencyStore idempotencyStore;
    private final RetryEngine retryEngine;
    private final DeadLetterQueue dlq;
    private final SyncJobRepository jobRepository;
    private final EventQueueSnapshotRepository snapshotRepository;
    private final RuntimeStateJson json;
    private final MeterRegistry meterRegistry;
    private final StatusBroadcaster broadcaster;
    private final RuntimeProperties runtime;

    // Transient in-memory state: event queues, worker threads, running flags.
    // The SyncJob itself (state + statistics) is durable in sync_jobs.
    private final Map<String, BlockingQueue<CDCEvent>> eventQueues = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    private final Map<String, Thread> workerThreads = new ConcurrentHashMap<>();

    public SyncOrchestrator(CaptureLifecycle captureLifecycle,
            PipelineDesignerService pipelineService,
            DestinationRouter router,
            EventIdempotencyStore idempotencyStore,
            RetryEngine retryEngine,
            DeadLetterQueue dlq,
            SyncJobRepository jobRepository,
            EventQueueSnapshotRepository snapshotRepository,
            RuntimeStateJson json,
            MeterRegistry meterRegistry,
            StatusBroadcaster broadcaster,
            RuntimeProperties runtime) {
        this.captureLifecycle = captureLifecycle;
        this.pipelineService = pipelineService;
        this.router = router;
        this.idempotencyStore = idempotencyStore;
        this.retryEngine = retryEngine;
        this.dlq = dlq;
        this.jobRepository = jobRepository;
        this.snapshotRepository = snapshotRepository;
        this.json = json;
        this.meterRegistry = meterRegistry;
        this.broadcaster = broadcaster;
        this.runtime = runtime;
    }

    /** Tenant-scoped map key so runtime state cannot collide across tenants. */
    static String key(String tenantId, String pipelineId) {
        return tenantId + ":" + pipelineId;
    }

    @Transactional
    public SyncJob start(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var tenantId = tenantContext.tenantId();
        var existing = findByPipeline(pipelineId, tenantContext);
        if (existing != null && existing.getState() == SyncState.RUNNING)
            return existing;

        var pipeline = pipelineService.get(pipelineId);

        // Start CDC capture if not already running
        var captureStatus = captureLifecycle.status(pipelineId, tenantContext);
        if (captureStatus != CaptureStatus.RUNNING) {
            captureLifecycle.start(pipelineId, null, tenantContext);
        }

        var job = new SyncJob(pipelineId).withRunning();
        persist(job, tenantContext);
        var key = key(tenantId.value(), pipelineId);
        var queue = new LinkedBlockingQueue<CDCEvent>(runtime.getSync().getQueueCapacity());
        eventQueues.put(key, queue);
        emit(job, tenantContext);

        // Multi-table: route events by source table to the correct TableMapping.
        // Build the dispatch map (table -> mapping) so each event is processed
        // against its own column/transform/filter pipeline.
        var tableMappings = pipeline.tableMappings();
        if (tableMappings.isEmpty()) {
            log.warn("Pipeline {} has no table mappings; nothing to sync", pipelineId);
            return job;
        }
        Map<String, TableMapping> mappingByTable = tableMappings.stream()
                .filter(tm -> tm.sourceTable() != null)
                .collect(Collectors.toMap(
                        TableMapping::sourceTable,
                        tm -> tm,
                        (a, b) -> a,
                        LinkedHashMap::new));
        log.info("Sync {} started: {} table mapping(s) [{}]",
                pipelineId, mappingByTable.size(), mappingByTable.keySet());

        // Race-safe start: register the flag BEFORE the worker starts. The worker
        // reads the flag under its own thread; setting it before
        // Thread.startVirtualThread
        // returns avoids the case where stop() runs in between, clears the flag,
        // and the new worker reads flag.get()==false on its first iteration and exits.
        var flag = new AtomicBoolean(true);
        runningFlags.put(key, flag);
        var thread = Thread
                .startVirtualThread(
                        () -> run(tenantContext, pipelineId, queue, mappingByTable,
                                pipeline.destination().connectionId()));
        workerThreads.put(key, thread);

        return job;
    }

    @Transactional
    public void stop(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = key(tenantContext.tenantId().value(), pipelineId);
        var flag = runningFlags.get(key);
        if (flag != null)
            flag.set(false);
        Optional.ofNullable(findByPipeline(pipelineId, tenantContext))
                .map(SyncJob::withStopped)
                .ifPresent(job -> {
                    persist(job, tenantContext);
                    emit(job, tenantContext);
                });
    }

    @Transactional(readOnly = true)
    public SyncJob get(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var job = findByPipeline(pipelineId, tenantContext);
        if (job == null)
            throw new NoSuchElementException("No sync job for pipeline: " + pipelineId);
        return job;
    }

    /**
     * Live-status event emitted on every state/statistics change for a pipeline.
     */
    private void emit(SyncJob job, TenantContext tenantContext) {
        // SSE keys are tenant-scoped (matching the runtime map keys) so one tenant
        // cannot subscribe to another tenant's stream.
        var key = key(tenantContext.tenantId().value(), job.getPipelineId());
        broadcaster.emit(key, "sync-status",
                Map.of("pipelineId", job.getPipelineId(),
                        "state", job.getState().name(),
                        "statistics", job.getStatistics()));
    }

    @Transactional(readOnly = true)
    public SyncState status(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var job = findByPipeline(pipelineId, tenantContext);
        return job != null ? job.getState() : SyncState.STOPPED;
    }

    @Transactional(readOnly = true)
    public List<SyncJob> list(TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        return jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantContext.tenantId().value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public SyncStatistics statistics(String pipelineId, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var job = findByPipeline(pipelineId, tenantContext);
        return job != null ? job.getStatistics() : new SyncStatistics(0, 0, 0, 0, 0, 0, 0);
    }

    public void submitEvent(String pipelineId, CDCEvent event, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        // Distinguish ingress from dispatch: helps dashboards see backpressure
        // when the worker can't keep up (high submit, low dispatch).
        MetricsHelper.increment(meterRegistry, "syncflow.sync.events.ingested",
                "pipeline", pipelineId,
                "operation", event.operation().name());
        var queue = eventQueues.get(key(tenantContext.tenantId().value(), pipelineId));
        if (queue == null) {
            // Pipeline not started — count the drop so dashboards see backpressure.
            MetricsHelper.increment(meterRegistry, "syncflow.sync.events.dropped",
                    "pipeline", pipelineId,
                    "reason", "pipeline_not_running");
            return;
        }
        var accepted = queue.offer(event);
        if (!accepted) {
            // Queue is at capacity: emit backpressure signal + DLQ the event.
            // Without DLQ we'd silently drop; that's worse than an explicit
            // failure because it breaks at-least-once delivery semantics.
            MetricsHelper.increment(meterRegistry, "syncflow.sync.events.dropped",
                    "pipeline", pipelineId,
                    "reason", "queue_full");
            FailureReason.permanentError("queue full")
                    .toString();
            // The DLQ add needs the event + a tenant context. The event
            // is already passed in; the tenant context is also in scope.
            dlq.add(pipelineId, event,
                    FailureReason.permanentError("queue full"),
                    0, tenantContext);
        }
    }

    private void run(TenantContext tenantContext, String pipelineId,
            BlockingQueue<CDCEvent> queue,
            Map<String, TableMapping> mappingByTable, String destConnectionId) {
        // DEEP FIX: TenantContext is passed as parameter through the entire
        // worker lifetime. We do NOT set the ThreadLocal — every downstream
        // call (persist, findByPipeline, emit) takes tenantContext as an
        // argument. This eliminates the virtual-thread ThreadLocal hazard.
        //
        // Guard: assert the ThreadLocal is unset on the fresh worker thread.
        // The old `clear()` in finally would have silently papered over a
        // re-introduced set() — a null-check at entry fails loudly instead.
        // Virtual threads are not pooled, so nothing leaks across workers.
        if (TenantContextHolder.get() != null) {
            throw new IllegalStateException(
                    "Worker thread must not carry a TenantContext ThreadLocal");
        }
        runInner(tenantContext, pipelineId, queue, mappingByTable, destConnectionId);
    }

    private void runInner(TenantContext tenantContext, String pipelineId, BlockingQueue<CDCEvent> queue,
            Map<String, TableMapping> mappingByTable, String destConnectionId) {
        var mapKey = key(tenantContext.tenantId().value(), pipelineId);
        var flag = runningFlags.get(mapKey);
        var eventsThisBatch = new ArrayList<CDCEvent>();
        var batchNum = new AtomicLong(0);
        var statsBuilder = new SyncStatisticsBuilder();

        while (flag != null && flag.get()) {
            try {
                eventsThisBatch.clear();
                queue.drainTo(eventsThisBatch, runtime.getSync().getBatchSize());
                if (eventsThisBatch.isEmpty()) {
                    var event = queue.poll(runtime.getSync().getPollTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    if (event != null)
                        eventsThisBatch.add(event);
                    else
                        continue;
                }

                statsBuilder.totalEvents.addAndGet(eventsThisBatch.size());

                // Per-batch buffers for batched writes (F6). The router
                // flushes + commits once per batch instead of per event.
                var writeBuffer = new HashMap<TableMapping, List<Map<String, Object>>>();
                var deleteBuffer = new HashMap<TableMapping, List<Map<String, Object>>>();

                // EventIds buffered this batch. Marked as processed ONLY after the
                // batch write to the destination succeeds (R1) — never before.
                var pendingIds = new ArrayList<PendingId>();

                // Tables we have already warned about (avoid log spam per event).
                final Set<String> warnedTables = ConcurrentHashMap.newKeySet();

                for (var event : eventsThisBatch) {
                    if (!flag.get())
                        break;
                    // Multi-table dispatch: look up the TableMapping for this
                    // event's source table. Events for tables not in the mapping
                    // are skipped (caller didn't configure them).
                    var mapping = mappingByTable.get(event.source().table());
                    if (mapping == null) {
                        statsBuilder.skippedEvents.incrementAndGet();
                        // Observability: distinct counters + log so a misconfigured
                        // pipeline (table drift in CDC) does not silently drop rows.
                        var unmappedTable = event.source().table();
                        if (warnedTables.add(unmappedTable)) {
                            log.warn("Sync {} received CDC event for unmapped table '{}' — pipeline.tableMappings()={}",
                                    pipelineId, unmappedTable, mappingByTable.keySet());
                        }
                        MetricsHelper.increment(meterRegistry, "syncflow.sync.events.skipped",
                                "pipeline", pipelineId,
                                "reason", "unmapped_table",
                                "table", unmappedTable);
                        continue;
                    }
                    var pending = processEvent(tenantContext, pipelineId, event, mapping, destConnectionId,
                            statsBuilder, writeBuffer, deleteBuffer);
                    if (pending != null) {
                        pendingIds.add(pending);
                    }
                    MetricsHelper.increment(meterRegistry, "syncflow.sync.events.dispatched",
                            "pipeline", pipelineId,
                            "table", mapping.sourceTable());
                }

                // Flush the accumulated writes as one batched DB operation.
                var batchOk = flushBatched(tenantContext, destConnectionId, writeBuffer, deleteBuffer);

                // mark processed only after the destination write SUCCEEDED.
                // If the batch failed, the events stay un-marked and will be
                // re-attempted on redelivery instead of being skipped as "done".
                if (batchOk) {
                    for (var id : pendingIds) {
                        // F14: atomic INSERT ... ON CONFLICT DO NOTHING. If a
                        // concurrent worker already marked this event, the insert
                        // is a no-op; retryEngine.success cleans up only when we
                        // were the marking call.
                        if (idempotencyStore.markProcessedIfAbsent(id.eventId(), tenantContext.tenantId().value(),
                                id.pipelineId())) {
                            retryEngine.success(id.eventId());
                        }
                    }
                }

                meterRegistry.gauge("syncflow.sync.queue.size", queue, BlockingQueue::size);
                MetricsHelper.increment(meterRegistry, "syncflow.sync.events.processed", eventsThisBatch.size(),
                        "pipeline", pipelineId);

                var stats = statsBuilder.build();
                Optional.ofNullable(findByPipeline(pipelineId, tenantContext))
                        .map(job -> job.withStatistics(stats))
                        .ifPresent(job -> {
                            persist(job, tenantContext);
                            emit(job, tenantContext);
                        });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                MetricsHelper.increment(meterRegistry, "syncflow.sync.errors",
                        "pipeline", pipelineId);
            }
        }

        Optional.ofNullable(findByPipeline(pipelineId, tenantContext))
                .map(SyncJob::withCompleted)
                .ifPresent(job -> {
                    persist(job, tenantContext);
                    emit(job, tenantContext);
                });
    }

    /**
     * Per-event processing: filter/transform, append the transformed row (or PK
     * map for deletes) to a per-batch accumulator. The caller flushes the
     * accumulator as a batched write so we make one DB call per N events instead
     * of one per event.
     *
     * Idempotency marking is deliberately NOT done here — the caller marks an
     * event only after the batch write to the destination SUCCEEDS. Marking
     * before the write would turn a batch failure into silent data loss: the
     * redelivered batch would be skipped by {@link #isProcessed} while the rows
     * never landed. See R1.
     *
     * @return the event's id + pipeline to mark as processed once the batch write
     *         succeeds, or null if the event was skipped/dropped (never buffered).
     */
    private PendingId processEvent(TenantContext tenantContext, String pipelineId, CDCEvent event,
            TableMapping mapping, String destConnectionId,
            SyncStatisticsBuilder stats,
            Map<TableMapping, List<Map<String, Object>>> writeBuffer,
            Map<TableMapping, List<Map<String, Object>>> deleteBuffer) {
        // Idempotency check
        var eventId = event.header().eventId();
        if (idempotencyStore.isProcessed(eventId)) {
            stats.skippedEvents.incrementAndGet();
            return null;
        }
        try {
            var payload = event.payload().after();
            if (payload == null && event.operation() == CDCOperation.DELETE) {
                payload = event.payload().before();
            }
            if (payload == null) {
                stats.skippedEvents.incrementAndGet();
                return null;
            }
            var pCtx = new ProcessingContext(null, mapping);
            var filter = new FilterProcessor();
            var transform = new TransformProcessor();
            var filtered = filter.process(payload, pCtx);
            if (filtered == null) {
                stats.skippedEvents.incrementAndGet();
                return null;
            }
            var transformed = transform.process(filtered, pCtx);

            if (event.operation() == CDCOperation.DELETE) {
                var pkMap = event.payload().primaryKeys();
                // A delete whose PK columns cannot be identified is a real
                // failure, not a silent skip — the destination would drift. Pass
                // it through so it fails loudly instead of being dropped and
                // marked processed.
                if (pkMap != null && !pkMap.isEmpty()) {
                    deleteBuffer.computeIfAbsent(mapping, k -> new ArrayList<>()).add(pkMap);
                } else {
                    throw new IllegalStateException(
                            "DELETE has no primary key columns; cannot identify the destination row for eventId="
                                    + eventId);
                }
            } else {
                writeBuffer.computeIfAbsent(mapping, k -> new ArrayList<>()).add(transformed);
            }
            stats.processedEvents.incrementAndGet();
            return new PendingId(eventId, pipelineId);
        } catch (Exception e) {
            // single disposition for a failure. evaluate() already DLQs
            // terminal failures once (and records the retry state for transient
            // ones); do NOT add a second unconditional dlq.add here — that
            // double-enqueued every failed event. The event is not marked
            // processed, so if it is redelivered it will be re-attempted.
            var reason = FailureReason.permanentError(e.getMessage());
            retryEngine.evaluate(pipelineId, event, reason, tenantContext);
            stats.failedEvents.incrementAndGet();
            return null;
        }
    }

    /**
     * Flush the per-batch write buffer: dispatch every buffered row and delete
     * as a single batched write to the destination router.
     *
     * @return true if every table's write succeeded; false if any failed (the
     *         caller then leaves the events un-marked so redelivery re-attempts
     *         them — see R1).
     */
    private boolean flushBatched(TenantContext tenantContext, String destConnectionId,
            Map<TableMapping, List<Map<String, Object>>> writeBuffer,
            Map<TableMapping, List<Map<String, Object>>> deleteBuffer) {
        // Union of all TableMappings in either buffer.
        var keys = new LinkedHashSet<TableMapping>();
        keys.addAll(writeBuffer.keySet());
        keys.addAll(deleteBuffer.keySet());
        boolean allOk = true;
        for (var mapping : keys) {
            var rows = writeBuffer.getOrDefault(mapping, List.of());
            var deletes = deleteBuffer.getOrDefault(mapping, List.of());
            var destColumns = mapping.columnMappings().stream()
                    .map(ColumnMapping::destinationColumn).toList();
            var events = new ArrayList<CDCEvent>();
            // Build synthetic CDCEvents so the router's batched API can group
            // by operation. For inserts/updates use INSERT op; for deletes use DELETE.
            // (The router doesn't read this; it's only used to satisfy the API.)
            // The router only reads event.operation(), event.source().table(),
            // event.payload().after() and event.payload().primaryKeys().
            // Build minimal CDCEvent representations directly without going
            // through the full constructor chain.
            for (var row : rows) {
                events.add(minimalEvent(mapping, CDCOperation.INSERT, row, null));
            }
            for (var pk : deletes) {
                events.add(minimalEvent(mapping, CDCOperation.DELETE, null, pk));
            }
            if (!events.isEmpty()) {
                var result = router.writeBatch(destConnectionId, events, destColumns);
                if (!result.success()) {
                    // on a batched failure we can't tell which event failed.
                    // DLQ each once (RetryEngine.evaluate handles the terminal
                    // path internally; do NOT double-enqueue). Leave the events
                    // un-marked so redelivery re-attempts the whole batch.
                    log.warn("Batched write failed for {} events: {}", events.size(), result.error());
                    var reason = FailureReason.transientError(result.error());
                    for (var event : events) {
                        retryEngine.evaluate(event.source().table(), event, reason, tenantContext);
                    }
                    allOk = false;
                }
            }
        }
        writeBuffer.clear();
        deleteBuffer.clear();
        return allOk;
    }

    /** Construct a minimal CDCEvent with just the fields the router reads. */
    private CDCEvent minimalEvent(TableMapping mapping, CDCOperation op,
            Map<String, Object> row, Map<String, Object> pkMap) {
        // the router groups by event.source().table(), which must be the
        // DESTINATION table name (the write target on the destination connection),
        // not the source table. Column mapping routes source columns to
        // destination columns; the table follows the same remap.
        var destTable = mapping.destinationTable() != null
                ? mapping.destinationTable()
                : mapping.destinationCollection();
        return new CDCEvent(
                new EventHeader(UUID.randomUUID().toString(),
                        destTable, "localhost", 0, 1, Map.of()),
                new EventSource(destTable, "", destTable,
                        "postgresql"),
                op,
                new EventPayload(row == null ? Map.of() : null,
                        row,
                        pkMap == null ? Map.of() : pkMap),
                new EventMetadata(0, Instant.now(), 0),
                null,
                new OffsetInformation("POSTGRESQL",
                        Map.of(), "", Instant.now()));
    }

    private SyncJob findByPipeline(String pipelineId, TenantContext tenantContext) {
        return jobRepository.findByTenantIdAndPipelineId(
                tenantContext.tenantId().value(), pipelineId)
                .map(this::toDomain)
                .orElse(null);
    }

    @Transactional
    private void persist(SyncJob job, TenantContext tenantContext) {
        var entity = jobRepository.findByTenantIdAndPipelineId(
                tenantContext.tenantId().value(), job.getPipelineId())
                .orElseGet(SyncJobEntity::new);
        entity.setId(job.getId());
        entity.setTenantId(tenantContext.tenantId().value());
        entity.setPipelineId(job.getPipelineId());
        entity.setState(job.getState().name());
        entity.setStatistics(json.toJson(job.getStatistics()));
        entity.setCreatedAt(job.getCreatedAt());
        entity.setUpdatedAt(Instant.now());
        jobRepository.save(entity);
    }

    private SyncJob toDomain(SyncJobEntity e) {
        return SyncJob.restore(e.getId(), e.getPipelineId(),
                SyncState.valueOf(e.getState()),
                json.fromJson(e.getStatistics(), SyncStatistics.class),
                e.getCreatedAt());
    }

    /**
     * Persist event queues on graceful shutdown to survive pod restarts.
     *
     * <p>
     * Snapshots each active event queue as a JSON array in
     * EventQueueSnapshotEntity. On startup,
     * rehydrateFromDatabase() loads these snapshots back into the queues.
     *
     * <p>
     * This ensures at-least-once delivery for CDC events during failover:
     * 1. Pod crashes mid-sync
     * 2. SyncOrchestrator.snapshotEventQueues() persists pending events
     * 3. New pod starts and calls rehydrateFromDatabase()
     * 4. Events are re-queued and processed (idempotency ensures no duplicates)
     */
    @PreDestroy
    public void snapshotEventQueues() {
        log.info("Snapshotting {} event queues on shutdown", eventQueues.size());
        var podName = System.getenv("HOSTNAME");

        for (var entry : eventQueues.entrySet()) {
            var key = entry.getKey();
            var queue = entry.getValue();

            try {
                if (queue.isEmpty()) {
                    log.debug("Event queue empty for key={}; skipping snapshot", key);
                    continue;
                }

                // Extract [tenantId, pipelineId] from key (format: "tenantId:pipelineId")
                var parts = key.split(":");
                if (parts.length != 2) {
                    log.warn("Invalid queue key format; skipping snapshot: key={}", key);
                    continue;
                }
                var tenantId = parts[0];
                var pipelineId = parts[1];

                // Serialize pending events to JSON
                var events = new java.util.ArrayList<>(queue);
                var eventsJson = json.toJson(events);

                // Persist snapshot
                var snapshot = new EventQueueSnapshotEntity(
                        tenantId,
                        pipelineId,
                        events.size(),
                        eventsJson,
                        podName,
                        "graceful_shutdown");
                snapshotRepository.save(snapshot);
                log.info("Snapshotted {} events for pipeline={} snapshot_id={}",
                        events.size(), pipelineId, snapshot.getId());
            } catch (Exception e) {
                log.error("Failed to snapshot event queue key={}", key, e);
            }
        }
    }

    /**
     * Rehydrate pending events from snapshots (called on startup).
     *
     * @return number of events recovered
     */
    public int rehydrateFromDatabase() {
        log.info("Rehydrating event queues from snapshots");
        int totalRecovered = 0;

        // Find all snapshots (typically 0-1 per tenant, but we handle multiple)
        var snapshots = snapshotRepository.findAll();
        for (var snapshot : snapshots) {
            try {
                var tenantId = snapshot.getTenantId();
                var pipelineId = snapshot.getPipelineId();
                var key = key(tenantId, pipelineId);

                // Deserialize events
                var events = json.fromJson(
                        snapshot.getEventsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.syncflow.core.cdc.CDCEvent>>() {
                        });

                // Requeue events (restore order)
                if (!events.isEmpty()) {
                    var queue = new LinkedBlockingQueue<CDCEvent>();
                    queue.addAll(events);
                    eventQueues.putIfAbsent(key, queue);
                    totalRecovered += events.size();
                    log.info("Rehydrated {} events for pipeline={}", events.size(), pipelineId);
                }

                // Clean up snapshot after successful rehydration
                snapshotRepository.delete(snapshot);
            } catch (Exception e) {
                log.error("Failed to rehydrate snapshot id={}", snapshot.getId(), e);
            }
        }

        // Clean up expired snapshots
        try {
            var expired = snapshotRepository.deleteExpired(java.time.Instant.now());
            if (expired > 0) {
                log.info("Cleaned up {} expired snapshots", expired);
            }
        } catch (Exception e) {
            log.warn("Failed to clean up expired snapshots", e);
        }

        log.info("Rehydration complete: {} events recovered", totalRecovered);
        return totalRecovered;
    }

    private static class SyncStatisticsBuilder {

        final AtomicLong totalEvents = new AtomicLong(0);
        final AtomicLong processedEvents = new AtomicLong(0);
        final AtomicLong failedEvents = new AtomicLong(0);
        final AtomicLong skippedEvents = new AtomicLong(0);
        final AtomicLong retries = new AtomicLong(0);
        final AtomicLong dlqCount = new AtomicLong(0);

        SyncStatistics build() {
            return new SyncStatistics(
                    totalEvents.get(), processedEvents.get(),
                    failedEvents.get(), skippedEvents.get(),
                    retries.get(), dlqCount.get(), 0);
        }
    }

    /** Event awaiting idempotency-mark after a successful batch write. */
    private record PendingId(String eventId, String pipelineId) {
    }
}
