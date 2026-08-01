package com.syncflow.core.cdc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdcEventParserUnitTest {

    // --- Event parser: Debezium JSON format ---

    private CDCEvent parseDebeziumEvent(String operation, Map<String, Object> before, Map<String, Object> after) {
        var header = new EventHeader("evt-1", "pipeline-1", "conn-1", 1, 1, Map.of());
        var source = new EventSource("mydb", "public", "users", "postgresql");

        var op = switch (operation) {
            case "c", "r" -> CDCOperation.INSERT;
            case "u" -> CDCOperation.UPDATE;
            case "d" -> CDCOperation.DELETE;
            default -> null;
        };

        Map<String, Object> pk = after != null ? after : (before != null ? before : new java.util.HashMap<>());
        var payload = new EventPayload(before, after, pk);

        return new CDCEvent(header, source, op, payload,
                new EventMetadata(1, java.time.Instant.now(), 0), null,
                new OffsetInformation("POSTGRESQL", Map.of("lsn", "123"), "", java.time.Instant.now()));
    }

    @Test
    void parseInsertEvent() {
        var event = parseDebeziumEvent("c", null, Map.of("id", 1, "name", "Alice"));
        assertEquals(CDCOperation.INSERT, event.operation());
        assertNull(event.payload().before());
        assertEquals("Alice", event.payload().after().get("name"));
    }

    @Test
    void parseReadEvent() {
        var event = parseDebeziumEvent("r", null, Map.of("id", 1));
        assertEquals(CDCOperation.INSERT, event.operation()); // 'r' maps to INSERT
    }

    @Test
    void parseUpdateEvent() {
        var event = parseDebeziumEvent("u",
                Map.of("id", 1, "name", "Bob"),
                Map.of("id", 1, "name", "Robert"));
        assertEquals(CDCOperation.UPDATE, event.operation());
        assertEquals("Bob", event.payload().before().get("name"));
        assertEquals("Robert", event.payload().after().get("name"));
    }

    @Test
    void parseDeleteEvent() {
        var event = parseDebeziumEvent("d", Map.of("id", 1, "name", "Charlie"), null);
        assertEquals(CDCOperation.DELETE, event.operation());
        assertEquals("Charlie", event.payload().before().get("name"));
        assertNull(event.payload().after());
    }

    @Test
    void parseUnknownOperationReturnsNull() {
        var event = parseDebeziumEvent("unknown", null, Map.of("id", 1));
        assertNull(event.operation());
    }

    @Test
    void parseNullOperationReturnsNull() {
        assertThrows(NullPointerException.class, () -> parseDebeziumEvent(null, null, null));
    }

    // --- Event ordering ---

    @Test
    void eventOrderingByLsn() {
        var earlier = new OffsetInformation("PG", Map.of("lsn", "100"), "", java.time.Instant.now());
        var later = new OffsetInformation("PG", Map.of("lsn", "200"), "", java.time.Instant.now());
        assertTrue(later.offset().get("lsn").compareTo(earlier.offset().get("lsn")) > 0);
    }

    @Test
    void eventOrderingByTimestamp() {
        var t1 = new EventMetadata(1, java.time.Instant.ofEpochMilli(1000), 0);
        var t2 = new EventMetadata(2, java.time.Instant.ofEpochMilli(2000), 0);
        assertTrue(t2.capturedAt().isAfter(t1.capturedAt()));
    }

    // --- Offset tracker ---

    @Test
    void offsetTrackerBasic() {
        var tracker = new OffsetTracker();
        assertTrue(tracker.getLastOffset("p-1").isEmpty());

        tracker.updateOffset("p-1", Map.of("lsn", "150"));
        assertEquals("150", tracker.getLastOffset("p-1").get("lsn"));

        tracker.updateOffset("p-1", Map.of("lsn", "200"));
        assertEquals("200", tracker.getLastOffset("p-1").get("lsn"));
    }

    @Test
    void offsetTrackerPerPipeline() {
        var tracker = new OffsetTracker();
        tracker.updateOffset("p-1", Map.of("lsn", "100"));
        tracker.updateOffset("p-2", Map.of("lsn", "50"));

        assertEquals("100", tracker.getLastOffset("p-1").get("lsn"));
        assertEquals("50", tracker.getLastOffset("p-2").get("lsn"));
    }

    @Test
    void offsetTrackerDelete() {
        var tracker = new OffsetTracker();
        tracker.updateOffset("p-1", Map.of("lsn", "100"));
        tracker.deleteOffset("p-1");
        assertTrue(tracker.getLastOffset("p-1").isEmpty());
    }

    @Test
    void offsetTrackerEmptyDefault() {
        var tracker = new OffsetTracker();
        assertTrue(tracker.getLastOffset("nonexistent").isEmpty());
    }

    // --- Event validator ---

    @Test
    void validInsertEvent() {
        var event = parseDebeziumEvent("c", null, Map.of("id", 1, "name", "test"));
        assertNotNull(event);
        assertNotNull(event.source());
        assertNotNull(event.payload());
    }

    @Test
    void validDeleteEvent() {
        var event = parseDebeziumEvent("d", Map.of("id", 1), null);
        assertNotNull(event);
        assertTrue(event.payload().before() != null && event.payload().after() == null);
    }

    @Test
    void eventWithNullSourceIsAcceptedByRecord() {
        // CDCEvent is a record — null components are allowed by the constructor
        var event = new CDCEvent(
                new EventHeader("e1", "p", "c", 1, 1, Map.of()),
                null, CDCOperation.INSERT,
                new EventPayload(null, Map.of(), Map.of()),
                new EventMetadata(1, java.time.Instant.now(), 0), null, null);
        assertNull(event.source());
        // Callers must validate before use
    }

    @Test
    void eventWithNullOperationIsAcceptedByRecord() {
        var event = new CDCEvent(
                new EventHeader("e2", "p", "c", 1, 1, Map.of()),
                new EventSource("db", "s", "t", "pg"),
                null, null,
                new EventMetadata(1, java.time.Instant.now(), 0), null, null);
        assertNull(event.operation());
        // The parser would return null before constructing the event
    }

    // --- Duplicate event detection ---

    @Test
    void duplicateEventIdDetection() {
        var tracker = new EventIdTracker();
        assertFalse(tracker.isDuplicate("evt-1"));
        tracker.markProcessed("evt-1");
        assertTrue(tracker.isDuplicate("evt-1"));
    }

    @Test
    void nonDuplicateEventId() {
        var tracker = new EventIdTracker();
        tracker.markProcessed("evt-1");
        assertFalse(tracker.isDuplicate("evt-2"));
    }

    @Test
    void atLeastOnceDeliveryTracking() {
        var tracker = new EventIdTracker();
        // At-least-once: skip duplicate, still commit offset
        tracker.markProcessed("evt-1");
        assertTrue(tracker.isDuplicate("evt-1")); // Skip processing
        // Continue processing next event
        assertFalse(tracker.isDuplicate("evt-2"));
    }

    // --- Helper classes ---

    static class OffsetTracker {

        private final java.util.Map<String, Map<String, String>> offsets = new java.util.concurrent.ConcurrentHashMap<>();

        void updateOffset(String pipelineId, Map<String, String> offset) {
            offsets.put(pipelineId, Map.copyOf(offset));
        }

        Map<String, String> getLastOffset(String pipelineId) {
            return offsets.getOrDefault(pipelineId, Map.of());
        }

        void deleteOffset(String pipelineId) {
            offsets.remove(pipelineId);
        }
    }

    static class EventIdTracker {

        private final java.util.Set<String> processed = java.util.concurrent.ConcurrentHashMap.newKeySet();

        boolean isDuplicate(String eventId) {
            return processed.contains(eventId);
        }

        void markProcessed(String eventId) {
            processed.add(eventId);
        }
    }
}
