package com.syncflow.api.sync;

import com.syncflow.api.cdc.CaptureLifecycle;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.snapshot.pipeline.FilterProcessor;
import com.syncflow.core.snapshot.pipeline.ProcessingContext;
import com.syncflow.core.snapshot.pipeline.TransformProcessor;
import com.syncflow.core.sync.FailureReason;
import com.syncflow.core.sync.SyncJob;
import com.syncflow.core.sync.SyncState;
import com.syncflow.core.sync.SyncStatistics;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
import com.syncflow.tenant.TenantSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SyncOrchestrator {

    private static final int QUEUE_CAPACITY = 10000;
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final CaptureLifecycle captureLifecycle;
    private final PipelineDesignerService pipelineService;
    private final DestinationRouter router;
    private final EventIdempotencyStore idempotencyStore;
    private final RetryEngine retryEngine;
    private final DeadLetterQueue dlq;
    private final MeterRegistry meterRegistry;
    private final StatusBroadcaster broadcaster;

    private final Map<String, SyncJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<CDCEvent>> eventQueues = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();
    private final Map<String, Thread> workerThreads = new ConcurrentHashMap<>();
    private final AtomicLong processed = new AtomicLong(0);

    public SyncOrchestrator(CaptureLifecycle captureLifecycle,
            PipelineDesignerService pipelineService,
            DestinationRouter router,
            EventIdempotencyStore idempotencyStore,
            RetryEngine retryEngine,
            DeadLetterQueue dlq,
            MeterRegistry meterRegistry,
            StatusBroadcaster broadcaster) {
        this.captureLifecycle = captureLifecycle;
        this.pipelineService = pipelineService;
        this.router = router;
        this.idempotencyStore = idempotencyStore;
        this.retryEngine = retryEngine;
        this.dlq = dlq;
        this.meterRegistry = meterRegistry;
        this.broadcaster = broadcaster;
    }

    /** Tenant-scoped map key so runtime state cannot collide across tenants. */
    static String key(String tenantId, String pipelineId) {
        return tenantId + ":" + pipelineId;
    }

    /** Tenant-scoped key derived from the current request/worker tenant. */
    private static String tenantKey(String pipelineId) {
        return key(TenantContextHolder.getTenantId().value(), pipelineId);
    }

    public SyncJob start(String pipelineId) {
        // Capture the tenant at request time so the background worker scopes its
        // DB work correctly (ThreadLocal does not cross virtual-thread boundaries).
        var tenantId = TenantContextHolder.getTenantId();
        var key = key(tenantId.value(), pipelineId);
        var existing = jobs.get(key);
        if (existing != null && existing.getState() == SyncState.RUNNING)
            return existing;

        // Start CDC capture if not already running
        var captureStatus = captureLifecycle.status(pipelineId);
        if (captureStatus != CaptureStatus.RUNNING) {
            captureLifecycle.start(pipelineId, null);
        }

        var job = new SyncJob(pipelineId).withRunning();
        jobs.put(key, job);
        runningFlags.put(key, new AtomicBoolean(true));
        var queue = new LinkedBlockingQueue<CDCEvent>(QUEUE_CAPACITY);
        eventQueues.put(key, queue);
        emit(job);

        var pipeline = pipelineService.get(pipelineId);
        var finalTm = pipeline.tableMappings().stream().findFirst().orElse(null);

        var thread = Thread
                .startVirtualThread(
                        () -> run(tenantId, pipelineId, queue, finalTm, pipeline.destination().connectionId()));
        workerThreads.put(key, thread);

        return job;
    }

    public void stop(String pipelineId) {
        var key = tenantKey(pipelineId);
        var flag = runningFlags.get(key);
        if (flag != null)
            flag.set(false);
        var job = jobs.get(key);
        if (job != null) {
            jobs.put(key, job.withStopped());
            emit(job);
        }
    }

    public SyncJob get(String pipelineId) {
        var key = tenantKey(pipelineId);
        var job = jobs.get(key);
        if (job == null)
            throw new NoSuchElementException("No sync job for pipeline: " + pipelineId);
        return job;
    }

    /**
     * Live-status event emitted on every state/statistics change for a pipeline.
     */
    private void emit(SyncJob job) {
        // SSE keys are tenant-scoped (matching the runtime map keys) so one tenant
        // cannot subscribe to another tenant's stream.
        var key = tenantKey(job.getPipelineId());
        broadcaster.emit(key, "sync-status",
                Map.of("pipelineId", job.getPipelineId(),
                        "state", job.getState().name(),
                        "statistics", job.getStatistics()));
    }

    public SyncState status(String pipelineId) {
        var job = jobs.get(tenantKey(pipelineId));
        return job != null ? job.getState() : SyncState.STOPPED;
    }

    public List<SyncJob> list() {
        var tenant = TenantContextHolder.getTenantId().value();
        return jobs.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tenant + ":"))
                .map(Map.Entry::getValue)
                .toList();
    }

    public SyncStatistics statistics(String pipelineId) {
        var job = jobs.get(tenantKey(pipelineId));
        return job != null ? job.getStatistics() : new SyncStatistics(0, 0, 0, 0, 0, 0, 0);
    }

    public void submitEvent(String pipelineId, CDCEvent event) {
        var queue = eventQueues.get(tenantKey(pipelineId));
        if (queue != null)
            queue.offer(event);
    }

    private void run(TenantId tenantId, String pipelineId,
            BlockingQueue<CDCEvent> queue,
            TableMapping mapping, String destConnectionId) {
        // The request thread's ThreadLocal won't reach this virtual thread; set the
        // tenant context so background DB work (DLQ, idempotency, routing) is scoped.
        // Carries a system identity so any background authz check resolves cleanly.
        TenantContextHolder.set(TenantSupport.workerContext(tenantId));
        try {
            runInner(pipelineId, queue, mapping, destConnectionId);
        } finally {
            com.syncflow.tenant.TenantContextHolder.clear();
        }
    }

    private void runInner(String pipelineId, BlockingQueue<CDCEvent> queue,
            TableMapping mapping, String destConnectionId) {
        var mapKey = tenantKey(pipelineId);
        var flag = runningFlags.get(mapKey);
        var eventsThisBatch = new ArrayList<CDCEvent>();
        var batchNum = new AtomicLong(0);
        var statsBuilder = new SyncStatisticsBuilder();

        while (flag != null && flag.get()) {
            try {
                eventsThisBatch.clear();
                queue.drainTo(eventsThisBatch, 100);
                if (eventsThisBatch.isEmpty()) {
                    var event = queue.poll(POLL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    if (event != null)
                        eventsThisBatch.add(event);
                    else
                        continue;
                }

                statsBuilder.totalEvents.addAndGet(eventsThisBatch.size());

                for (var event : eventsThisBatch) {
                    if (!flag.get())
                        break;
                    processEvent(pipelineId, event, mapping, destConnectionId, statsBuilder, batchNum);
                }

                meterRegistry.gauge("syncflow.sync.queue.size", queue, BlockingQueue::size);
                meterRegistry.counter("syncflow.sync.events.processed",
                        "pipeline", pipelineId).increment(eventsThisBatch.size());

                var stats = statsBuilder.build();
                var job = jobs.get(mapKey);
                if (job != null) {
                    jobs.put(mapKey, job.withStatistics(stats));
                    emit(job);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                meterRegistry.counter("syncflow.sync.errors",
                        "pipeline", pipelineId).increment();
            }
        }

        var finalJob = jobs.get(mapKey);
        if (finalJob != null) {
            jobs.put(mapKey, finalJob.withCompleted());
            emit(finalJob);
        }
    }

    private void processEvent(String pipelineId, CDCEvent event,
            TableMapping mapping, String destConnectionId,
            SyncStatisticsBuilder stats, AtomicLong batchNum) {
        // Idempotency check
        var eventId = event.header().eventId();
        if (idempotencyStore.isProcessed(eventId)) {
            stats.skippedEvents.incrementAndGet();
            return;
        }

        try {
            // Transform payload using pipeline mappings
            var payload = event.payload().after();
            if (payload == null && event.operation() == com.syncflow.core.cdc.CDCOperation.DELETE) {
                payload = event.payload().before();
            }
            if (payload == null)
                return;

            var pCtx = new ProcessingContext(null, mapping);
            var filter = new FilterProcessor();
            var transform = new TransformProcessor();

            var filtered = filter.process(payload, pCtx);
            if (filtered == null) {
                stats.skippedEvents.incrementAndGet();
                return;
            }
            var transformed = transform.process(filtered, pCtx);

            var destColumns = mapping.columnMappings().stream()
                    .map(ColumnMapping::destinationColumn)
                    .toList();

            var result = router.write(destConnectionId, event, destColumns);

            if (result.success()) {
                idempotencyStore.markProcessed(eventId);
                retryEngine.success(eventId);
                stats.processedEvents.incrementAndGet();
            } else {
                var reason = FailureReason.transientError(result.error());
                var decision = retryEngine.evaluate(pipelineId, event, reason);
                if (!decision.shouldRetry()) {
                    stats.failedEvents.incrementAndGet();
                }
                stats.retries.incrementAndGet();
            }
        } catch (Exception e) {
            var reason = FailureReason.permanentError(e.getMessage());
            retryEngine.evaluate(pipelineId, event, reason);
            stats.failedEvents.incrementAndGet();
        }
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
}
