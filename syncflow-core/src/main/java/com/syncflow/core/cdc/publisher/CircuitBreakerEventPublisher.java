package com.syncflow.core.cdc.publisher;

import com.syncflow.core.cdc.CDCEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit-breaker decorator around an {@link EventPublisher}.
 *
 * <p>
 * Wraps any {@code EventPublisher} delegate (normally
 * {@link BoundedQueueEventPublisher})
 * and guards {@link #publish} calls with a Resilience4j {@link CircuitBreaker}.
 * When the downstream write path is failing (the circuit opens), new events are
 * rejected immediately rather than piling up in the queue, preventing memory
 * pressure and giving the destination time to recover.
 *
 * <h3>State machine</h3>
 *
 * <pre>
 *  CLOSED ──(failure rate ≥ threshold)──► OPEN ──(wait duration)──► HALF_OPEN
 *    ▲                                                                    │
 *    └──────────────────(probe succeeds)─────────────────────────────────┘
 * </pre>
 *
 * <h3>Configuration defaults</h3>
 * <ul>
 * <li>Failure rate threshold: 50 % of calls in a 10-event sliding window</li>
 * <li>Open state wait duration: 30 seconds</li>
 * <li>Half-open probe calls: 5</li>
 * <li>Slow call threshold: events taking &gt; 2 s count as failures</li>
 * </ul>
 * All values are configurable via the {@link Builder}.
 *
 * <h3>On open circuit</h3>
 * {@link CallNotPermittedException} is caught silently; the event is counted
 * in {@link #totalRejected()} and a WARN is logged once per state transition.
 * Callers (e.g. {@code CaptureLifecycle}) register a state-change listener to
 * emit metrics and surface alerts.
 */
public class CircuitBreakerEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventPublisher.class);

    private final EventPublisher delegate;
    private final CircuitBreaker circuitBreaker;
    private final AtomicLong totalRejected = new AtomicLong(0);

    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a publisher with default circuit-breaker settings.
     *
     * @param pipelineId
     *            used to name the circuit breaker (one CB per pipeline)
     * @param delegate
     *            the real publisher to delegate successful calls to
     */
    public CircuitBreakerEventPublisher(String pipelineId, EventPublisher delegate) {
        this(pipelineId, delegate, defaultConfig());
    }

    /**
     * Creates a publisher with custom circuit-breaker configuration.
     */
    public CircuitBreakerEventPublisher(String pipelineId, EventPublisher delegate,
            CircuitBreakerConfig config) {
        this.delegate = delegate;
        this.circuitBreaker = CircuitBreakerRegistry.of(config)
                .circuitBreaker("cdc-publisher-" + pipelineId, config);
        // Log state transitions so ops teams know when a pipeline circuit opens/closes.
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "CDC circuit breaker state change pipeline={} transition={}→{}",
                        pipelineId,
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> {
                    // Only log every 1000 rejections to avoid log flooding while OPEN.
                    if (totalRejected.get() % 1000 == 0) {
                        log.warn("CDC circuit breaker OPEN — rejecting events pipeline={} totalRejected={}",
                                pipelineId, totalRejected.get());
                    }
                });
    }

    // ── EventPublisher ────────────────────────────────────────────────────────

    @Override
    public void publish(CDCEvent event) {
        try {
            circuitBreaker.executeRunnable(() -> delegate.publish(event));
        } catch (CallNotPermittedException e) {
            // Circuit is OPEN: count and discard. The CDC engine keeps running;
            // this back-pressure signal prevents the in-process queue from filling
            // while the destination is unreachable.
            totalRejected.incrementAndGet();
        }
        // Any other exception from delegate.publish() is recorded by the circuit
        // breaker automatically (counts toward the failure-rate threshold) and
        // re-thrown so the caller (DebeziumCdcConnector lambda) can handle it.
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public long count() {
        return delegate.count();
    }

    // ── observable state ──────────────────────────────────────────────────────

    /** Events rejected while the circuit was OPEN. */
    public long totalRejected() {
        return totalRejected.get();
    }

    /** Current state: CLOSED, OPEN, or HALF_OPEN. */
    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }

    /** Exposes the underlying {@link CircuitBreaker} for metrics registration. */
    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    // ── drain (passthrough for BoundedQueueEventPublisher compatibility) ──────

    /**
     * Drain events from the delegate if it is a {@link BoundedQueueEventPublisher}.
     * No-op for other delegate types (e.g. {@link KafkaEventPublisher}).
     */
    public List<CDCEvent> drain(int maxEvents) {
        if (delegate instanceof BoundedQueueEventPublisher bq) {
            return bq.drain(maxEvents);
        }
        return List.of();
    }

    // ── default config ────────────────────────────────────────────────────────

    /**
     * Default circuit-breaker config tuned for CDC event publishing:
     * <ul>
     * <li>COUNT_BASED sliding window of 10 events — fast reaction to bursts</li>
     * <li>Open when ≥ 50 % of the last 10 calls fail</li>
     * <li>Stay open for 30 s — enough for a transient destination outage</li>
     * <li>5 probe calls in HALF_OPEN before deciding to close/reopen</li>
     * <li>Slow calls (&gt; 2 s) count as failures — writers should not block
     * CDC</li>
     * <li>Minimum 5 calls before failure-rate is evaluated (avoids false trips
     * on start-up)</li>
     * <li>Automatic transition from OPEN to HALF_OPEN — no external trigger</li>
     * </ul>
     */
    public static CircuitBreakerConfig defaultConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50f) // 50 % failure rate opens the circuit
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slowCallRateThreshold(80f) // 80 % slow calls also opens the circuit
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    // ── builder ───────────────────────────────────────────────────────────────

    public static Builder builder(String pipelineId, EventPublisher delegate) {
        return new Builder(pipelineId, delegate);
    }

    public static final class Builder {

        private final String pipelineId;
        private final EventPublisher delegate;
        private int slidingWindowSize = 10;
        private float failureRateThreshold = 50f;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int permittedCallsInHalfOpen = 5;
        private Duration slowCallThreshold = Duration.ofSeconds(2);
        private int minimumCalls = 5;

        private Builder(String pipelineId, EventPublisher delegate) {
            this.pipelineId = pipelineId;
            this.delegate = delegate;
        }

        public Builder slidingWindowSize(int size) {
            this.slidingWindowSize = size;
            return this;
        }

        public Builder failureRateThreshold(float threshold) {
            this.failureRateThreshold = threshold;
            return this;
        }

        public Builder waitDurationInOpenState(Duration duration) {
            this.waitDurationInOpenState = duration;
            return this;
        }

        public Builder permittedCallsInHalfOpen(int calls) {
            this.permittedCallsInHalfOpen = calls;
            return this;
        }

        public Builder slowCallThreshold(Duration threshold) {
            this.slowCallThreshold = threshold;
            return this;
        }

        public Builder minimumCalls(int calls) {
            this.minimumCalls = calls;
            return this;
        }

        public CircuitBreakerEventPublisher build() {
            var config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(slidingWindowSize)
                    .failureRateThreshold(failureRateThreshold)
                    .waitDurationInOpenState(waitDurationInOpenState)
                    .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)
                    .slowCallDurationThreshold(slowCallThreshold)
                    .slowCallRateThreshold(80f)
                    .minimumNumberOfCalls(minimumCalls)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();
            return new CircuitBreakerEventPublisher(pipelineId, delegate, config);
        }
    }
}
