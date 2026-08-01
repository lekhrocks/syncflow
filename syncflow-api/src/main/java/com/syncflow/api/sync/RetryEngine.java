package com.syncflow.api.sync;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.sync.FailureReason;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RetryEngine {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private final Map<String, RetryState> retries = new ConcurrentHashMap<>();
    private final DeadLetterQueue dlq;
    private final MeterRegistry meterRegistry;

    public RetryEngine(DeadLetterQueue dlq, MeterRegistry meterRegistry) {
        this.dlq = dlq;
        this.meterRegistry = meterRegistry;
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
