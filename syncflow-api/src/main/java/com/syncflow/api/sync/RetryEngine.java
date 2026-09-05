package com.syncflow.api.sync;

import com.syncflow.api.config.RuntimeProperties;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.sync.FailureReason;
import com.syncflow.tenant.TenantContext;
import com.syncflow.api.config.MetricsHelper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RetryEngine {

    private final Map<String, RetryState> retries = new ConcurrentHashMap<>();
    private final DeadLetterQueue dlq;
    private final MeterRegistry meterRegistry;
    private final RuntimeProperties runtime;

    public RetryEngine(DeadLetterQueue dlq, MeterRegistry meterRegistry, RuntimeProperties runtime) {
        this.dlq = dlq;
        this.meterRegistry = meterRegistry;
        this.runtime = runtime;
    }

    public RetryDecision evaluate(String pipelineId, CDCEvent event, FailureReason reason,
            TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var key = event.header().eventId();
        var state = retries.computeIfAbsent(key, k -> new RetryState());

        if (!reason.retryable() || state.count.get() >= runtime.getRetry().getMaxAttempts()) {
            dlq.add(pipelineId, event, reason, state.count.get(), tenantContext);
            retries.remove(key);
            MetricsHelper.increment(meterRegistry, "syncflow.sync.dlq.added",
                    "pipeline", pipelineId);
            return new RetryDecision(false, Duration.ZERO);
        }

        state.count.incrementAndGet();
        long baseMs = runtime.getRetry().getBaseDelay().toMillis();
        var delay = Duration.ofMillis(baseMs * (1L << (state.count.get() - 1)));
        MetricsHelper.increment(meterRegistry, "syncflow.sync.retries",
                "pipeline", pipelineId);
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
