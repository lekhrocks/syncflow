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
 * Replaces {@link InMemoryEventPublisher} which grows without bound and risks
 * OOM.
 * <p>
 * When the queue is full, the oldest event is dropped and a warning is logged
 * so
 * data-loss is always visible (never silent).
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
        if (!queue.offer(event)) {
            // Queue full: drop oldest, enqueue newest so we always have the latest state
            var dropped = queue.poll();
            queue.offer(event);
            totalDropped.incrementAndGet();
            log.warn("CDC event queue full (capacity={}), dropped event id={} operation={}",
                    queue.remainingCapacity() + queue.size(),
                    dropped != null ? dropped.header().eventId() : "unknown",
                    dropped != null ? dropped.operation() : "unknown");
        }
        totalPublished.incrementAndGet();
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
