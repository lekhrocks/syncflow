package com.syncflow.core.cdc.publisher;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BoundedQueueEventPublisher")
class BoundedQueueEventPublisherTest {

    private BoundedQueueEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new BoundedQueueEventPublisher(10);
    }

    private CDCEvent event(String id) {
        return new CDCEvent(
                new EventHeader(id, "pipeline-1", "conn-1", 1, 1, Map.of()),
                new EventSource("db", "public", "users", "postgresql"),
                CDCOperation.INSERT,
                new EventPayload(null, Map.of("id", 1), Map.of("id", 1)),
                new EventMetadata(1, Instant.now(), 0),
                null,
                new OffsetInformation("POSTGRESQL", Map.of("lsn", "0/1"), "", Instant.now()));
    }

    @Nested
    @DisplayName("publish()")
    class Publish {

        @Test
        void countIncreasesOnPublish() {
            publisher.publish(event("e1"));
            assertEquals(1, publisher.count());
        }

        @Test
        void totalPublishedTracked() {
            publisher.publish(event("e1"));
            publisher.publish(event("e2"));
            assertEquals(2, publisher.totalPublished());
        }

        @Test
        void noDropsWhenUnderCapacity() {
            for (int i = 0; i < 10; i++) {
                publisher.publish(event("e" + i));
            }
            assertEquals(0, publisher.totalDropped());
            assertEquals(10, publisher.count());
        }

        @Test
        void dropsOldestWhenQueueFull() {
            // Fill queue
            for (int i = 0; i < 10; i++) {
                publisher.publish(event("e" + i));
            }
            // One more should cause a drop
            publisher.publish(event("e10"));
            assertEquals(1, publisher.totalDropped());
            assertEquals(10, publisher.count()); // still 10, oldest dropped
        }

        @Test
        void totalPublishedIncludesDropped() {
            for (int i = 0; i < 12; i++) {
                publisher.publish(event("e" + i));
            }
            assertEquals(12, publisher.totalPublished());
            assertEquals(2, publisher.totalDropped());
        }

        @Test
        void latestEventPreservedAfterDrop() {
            for (int i = 0; i < 10; i++) {
                publisher.publish(event("e" + i));
            }
            publisher.publish(event("latest"));
            var events = publisher.peek();
            assertTrue(events.stream().anyMatch(e -> "latest".equals(e.header().eventId())));
        }
    }

    @Nested
    @DisplayName("drain()")
    class Drain {

        @Test
        void drainUpToMaxEvents() {
            for (int i = 0; i < 5; i++)
                publisher.publish(event("e" + i));
            var drained = publisher.drain(3);
            assertEquals(3, drained.size());
            assertEquals(2, publisher.count());
        }

        @Test
        void drainAllWhenMaxExceedsSize() {
            for (int i = 0; i < 4; i++)
                publisher.publish(event("e" + i));
            var drained = publisher.drain(100);
            assertEquals(4, drained.size());
            assertEquals(0, publisher.count());
        }

        @Test
        void drainReturnsEmptyWhenQueueEmpty() {
            var drained = publisher.drain(10);
            assertNotNull(drained);
            assertTrue(drained.isEmpty());
        }
    }

    @Nested
    @DisplayName("peek()")
    class Peek {

        @Test
        void peekIsNonDestructive() {
            publisher.publish(event("e1"));
            publisher.peek();
            assertEquals(1, publisher.count());
        }

        @Test
        void peekReturnsCurrentContents() {
            publisher.publish(event("p1"));
            publisher.publish(event("p2"));
            var peeked = publisher.peek();
            assertEquals(2, peeked.size());
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {

        @Test
        void closeClearsQueue() {
            publisher.publish(event("e1"));
            publisher.publish(event("e2"));
            publisher.close();
            assertEquals(0, publisher.count());
        }
    }

    @Nested
    @DisplayName("default capacity")
    class DefaultCapacity {

        @Test
        void defaultCapacityIs10000() {
            var defaultPublisher = new BoundedQueueEventPublisher();
            assertEquals(BoundedQueueEventPublisher.DEFAULT_CAPACITY, 10_000);
            // publish up to capacity without drop
            for (int i = 0; i < 100; i++)
                defaultPublisher.publish(event("e" + i));
            assertEquals(0, defaultPublisher.totalDropped());
            defaultPublisher.close();
        }
    }

    @Nested
    @DisplayName("count() via EventPublisher interface")
    class InterfaceCount {

        @Test
        void countAccessibleViaInterface() {
            EventPublisher ep = publisher;
            ep.publish(event("e1"));
            assertEquals(1, ep.count());
        }
    }
}
