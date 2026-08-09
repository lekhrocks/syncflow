package com.syncflow.core.cdc.publisher;

import com.syncflow.core.cdc.CDCEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, thread-safe event publisher backed by an {@link ArrayBlockingQueue}.
 * <p>
 * When the queue is full, {@link #publish(CDCEvent)} BLOCKS (via {@code put})
 * instead of dropping events. This applies backpressure to the CDC producer
 * (Debezium engine) so the sink can catch up — the alternative (drop-oldest)
 * silently loses change events under load, which is unacceptable for a CDC
 * platform. Data loss is never silent.
 * <p>
 * Consumers drain via {@link #drain(int)} (non-blocking, up to maxEvents).
 */
public class BoundedQueueEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BoundedQueueEventPublisher.class);

    /**
     * Default capacity — configurable per pipeline via the constructor.
     */
    public static final int DEFAULT_CAPACITY = 10_000;

    private final BlockingQueue<CDCEvent> queue;
    private final AtomicLong totalPublished = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);

    public BoundedQueueEventPublisher() {
        this(DEFAULT_CAPACITY);
    }

    public BoundedQueueEventPublisher(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void publish(CDCEvent event) {
        // Blocking put: backpressure the producer when the sink is slow. Never
        // drop — a dropped change event is silent data loss.
        try {
            queue.put(event);
            totalPublished.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("CDC publish interrupted (queue full); event id={} dropped",
                    event.header().eventId());
            totalDropped.incrementAndGet();
        }
    }

    @Override
    public void flush() {
        // no-op: consumers drain via drain()
    }

    @Override
    public void close() {
        queue.clear();
    }

    /**
     * Drain up to {@code maxEvents} events from the queue, non-blocking.
     */
    public List<CDCEvent> drain(int maxEvents) {
        var batch = new ArrayList<CDCEvent>(maxEvents);
        queue.drainTo(batch, maxEvents);
        return batch;
    }

    /**
     * Peek at all events currently in the queue (non-destructive, for
     * status/testing).
     */
    public List<CDCEvent> peek() {
        return List.copyOf(queue);
    }

    @Override
    public long count() {
        return queue.size();
    }

    public long totalPublished() {
        return totalPublished.get();
    }

    public long totalDropped() {
        return totalDropped.get();
    }
}
