package com.syncflow.api.sync;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.sync.FailureReason;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry scheduling for transient sync failures.
 * <p>
 * On a retryable failure, the event is RE-ENQUEUED (via the registered
 * re-enqueue callback) after an exponential backoff instead of only being
 * counted. Exhausting {@link #MAX_RETRIES} or a permanent error moves the event
 * to the DLQ. The counting semantics (shouldRetry + delay) are preserved so
 * callers and unit tests keep working.
 */
@Component
public class RetryEngine {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private final Map<String, RetryState> retries = new ConcurrentHashMap<>();
    private final DeadLetterQueue dlq;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** (tenantId, pipelineId, event) → re-enqueue into the sync engine. Set by the owner. */
    private volatile RetryReenqueue reenqueue;

    /** Re-enqueue hook carrying the tenant captured at evaluate() time. */
    @FunctionalInterface
    public interface RetryReenqueue {

        void accept(String tenantId, String pipelineId, CDCEvent event);
    }

    public RetryEngine(DeadLetterQueue dlq, MeterRegistry meterRegistry) {
        this.dlq = dlq;
        this.meterRegistry = meterRegistry;
    }

    /** The sync engine wires its {@code submitEvent} here so retries actually re-deliver. */
    public void setReenqueue(RetryReenqueue reenqueue) {
        this.reenqueue = reenqueue;
    }

    public RetryDecision evaluate(String pipelineId, CDCEvent event, FailureReason reason) {
        var key = event.header().eventId();
        var state = retries.computeIfAbsent(key, k -> new RetryState());

        if (!reason.retryable() || state.count.get() >= MAX_RETRIES) {
            dlq.add(pipelineId, event, reason, state.count.get());
            retries.remove(key);
            meterRegistry.counter("syncflow.sync.dlq.added",
                    "pipeline", pipelineId).increment();
            return new RetryDecision(false, Duration.ZERO);
        }

        state.count.incrementAndGet();
        var delay = Duration.ofMillis(BASE_DELAY_MS * (1L << (state.count.get() - 1)));
        meterRegistry.counter("syncflow.sync.retries",
                "pipeline", pipelineId).increment();

        // Actually re-deliver after the backoff (not just count). The re-enqueue
        // callback is registered by SyncOrchestrator; a null callback degrades to
        // the previous count-only behavior. The tenant is captured here (the
        // worker thread carries it) and re-established in the scheduled task so
        // the re-submit is tenant-scoped.
        var reenqueue = this.reenqueue;
        if (reenqueue != null) {
            var tenantId = com.syncflow.tenant.TenantContextHolder.getTenantId().value();
            scheduler.schedule(() -> reenqueue.accept(tenantId, pipelineId, event),
                    delay.toMillis(), TimeUnit.MILLISECONDS);
        }
        return new RetryDecision(true, delay);
    }

    public void success(String eventId) {
        retries.remove(eventId);
    }

    public int activeRetries() {
        return retries.size();
    }

    public record RetryDecision(boolean shouldRetry, Duration delay) {
    }

    private static class RetryState {

        final AtomicInteger count = new AtomicInteger(0);
    }
}
