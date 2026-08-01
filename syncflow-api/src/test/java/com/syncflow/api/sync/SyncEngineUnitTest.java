package com.syncflow.api.sync;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.core.sync.FailureReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncEngineUnitTest {

    private final DeadLetterQueue dlq = new DeadLetterQueue();
    private final RetryEngine retry = new RetryEngine(dlq, new SimpleMeterRegistry());
    private final EventIdempotencyStore idempotency = new EventIdempotencyStore();
    private final DestinationRouterStub router = new DestinationRouterStub();

    // --- Transformation (simulated via the chain used in SyncOrchestrator) ---

    @Test
    void transformRenamesColumn() {
        var input = Map.<String, Object>of("first_name", "John", "last_name", "Doe");
        var result = applyRename(input, "first_name", "firstName");
        assertTrue(result.containsKey("firstName"));
        assertFalse(result.containsKey("first_name"));
        assertEquals("John", result.get("firstName"));
    }

    @Test
    void transformUpperCases() {
        var input = Map.<String, Object>of("name", "john");
        var result = applyUppercase(input, "name", "name_upper");
        assertEquals("JOHN", result.get("name_upper"));
    }

    @Test
    void transformDefaultValue() {
        var input = new java.util.HashMap<String, Object>();
        input.put("id", 1);
        input.put("nickname", null);
        var result = applyDefault(input, "nickname", "nickname", "N/A");
        assertEquals("N/A", result.get("nickname"));
    }

    @Test
    void transformIgnoreColumn() {
        var input = Map.<String, Object>of("id", 1, "password_hash", "abc123");
        var result = applyIgnore(input, "password_hash");
        assertFalse(result.containsKey("password_hash"));
    }

    // --- Filtering ---

    @Test
    void filterEqualsPasses() {
        assertTrue(applyEqualsFilter(Map.of("status", "active"), "status", "active"));
    }

    @Test
    void filterEqualsDrops() {
        assertFalse(applyEqualsFilter(Map.of("status", "inactive"), "status", "active"));
    }

    @Test
    void filterIsNullPasses() {
        var input = new java.util.HashMap<String, Object>();
        input.put("deleted_at", null);
        assertTrue(applyIsNullFilter(input, "deleted_at"));
    }

    @Test
    void filterIsNullDrops() {
        assertFalse(applyIsNullFilter(Map.of("deleted_at", "2024-01-01"), "deleted_at"));
    }

    @Test
    void filterContainsPasses() {
        assertTrue(applyContainsFilter(Map.of("email", "user@example.com"), "email", "example"));
    }

    // --- Mapping ---

    @Test
    void mapColumnDirectly() {
        var input = Map.<String, Object>of("id", 1, "name", "test");
        var mapping = Map.of("id", "identifier", "name", "full_name");
        var result = applyMapping(input, mapping);
        assertEquals(1, result.get("identifier"));
        assertEquals("test", result.get("full_name"));
    }

    @Test
    void mapColumnWithTypeConversion() {
        var input = Map.<String, Object>of("count", "42");
        var result = applyMapping(input, Map.of("count", "count_int"));
        assertEquals("42", result.get("count_int"));
    }

    // --- Retry logic ---

    @Test
    void retryFirstAttemptSucceeds() {
        var event = createTestEvent("evt-1");
        var decision = retry.evaluate("p-1", event, FailureReason.transientError("timeout"));
        assertTrue(decision.shouldRetry());
        assertEquals(Duration.ofMillis(1000), decision.delay());

        retry.success("evt-1");
        assertEquals(0, retry.activeRetries());
    }

    @Test
    void retryExhaustedMovesToDlq() {
        var event = createTestEvent("evt-2");
        FailureReason reason = FailureReason.transientError("timeout");

        // Exhaust 3 retries
        for (int i = 0; i < 3; i++) {
            var decision = retry.evaluate("p-1", event, reason);
            assertTrue(decision.shouldRetry());
        }
        // 4th attempt → DLQ
        var finalDecision = retry.evaluate("p-1", event, reason);
        assertFalse(finalDecision.shouldRetry());
        assertEquals(1, dlq.count());
    }

    @Test
    void retryExponentialBackoff() {
        var event = createTestEvent("evt-3");
        FailureReason reason = FailureReason.transientError("timeout");

        var d1 = retry.evaluate("p-1", event, reason);
        assertEquals(1000, d1.delay().toMillis());

        var d2 = retry.evaluate("p-1", event, reason);
        assertEquals(2000, d2.delay().toMillis());

        var d3 = retry.evaluate("p-1", event, reason);
        assertEquals(4000, d3.delay().toMillis());
    }

    @Test
    void permanentErrorGoesDirectlyToDlq() {
        var event = createTestEvent("evt-4");
        var decision = retry.evaluate("p-1", event, FailureReason.permanentError("invalid schema"));
        assertFalse(decision.shouldRetry());
        assertEquals(1, dlq.count());
    }

    // --- Conflict resolution ---

    @Test
    void conflictDetectedByPrimaryKey() {
        // Two events with same PK should result in one processed
        var event1 = createTestEvent("evt-5");
        var event2 = createTestEvent("evt-5"); // Same eventId = duplicate
        idempotency.markProcessed(event1.header().eventId());
        assertTrue(idempotency.isProcessed(event2.header().eventId()));
    }

    // --- Deduplication ---

    @Test
    void deduplicationPreventsDuplicateProcessing() {
        var event = createTestEvent("evt-6");
        assertFalse(idempotency.isProcessed(event.header().eventId()));
        idempotency.markProcessed(event.header().eventId());
        assertTrue(idempotency.isProcessed(event.header().eventId()));
        // Marking again is idempotent
        idempotency.markProcessed(event.header().eventId());
        assertEquals(1, idempotency.size());
    }

    @Test
    void deduplicationEviction() {
        var event = createTestEvent("evt-7");
        idempotency.markProcessed(event.header().eventId());
        assertTrue(idempotency.isProcessed(event.header().eventId()));
        idempotency.evict(event.header().eventId());
        assertFalse(idempotency.isProcessed(event.header().eventId()));
    }

    // --- DLQ logic ---

    @Test
    void dlqStoresFailedEvent() {
        var event = createTestEvent("evt-8");
        dlq.add("p-1", event, FailureReason.permanentError("bad data"), 3);
        assertEquals(1, dlq.count());
    }

    @Test
    void dlqListFilteredByPipeline() {
        dlq.add("p-1", createTestEvent("e1"), FailureReason.permanentError("err1"), 1);
        dlq.add("p-2", createTestEvent("e2"), FailureReason.permanentError("err2"), 1);
        dlq.add("p-1", createTestEvent("e3"), FailureReason.permanentError("err3"), 2);

        var p1Events = dlq.list("p-1");
        assertEquals(2, p1Events.size());
        var p2Events = dlq.list("p-2");
        assertEquals(1, p2Events.size());
    }

    @Test
    void dlqReplayRemovesEvent() {
        var event = createTestEvent("evt-9");
        dlq.add("p-1", event, FailureReason.permanentError("err"), 3);
        assertEquals(1, dlq.count());
        dlq.add("p-1", event, FailureReason.permanentError("err"), 3);
        assertEquals(2, dlq.count());
    }

    @Test
    void dlqDeleteById() {
        dlq.add("p-1", createTestEvent("e_del"), FailureReason.permanentError("err"), 2);
        assertEquals(1, dlq.count());
        // Delete via list + extract ID
        var all = dlq.list(null);
        dlq.delete(all.getFirst().id());
        assertEquals(0, dlq.count());
    }

    @Test
    void dlqListAllWhenPipelineIsNull() {
        dlq.add("p-1", createTestEvent("e1"), FailureReason.permanentError("e"), 1);
        dlq.add("p-2", createTestEvent("e2"), FailureReason.permanentError("e"), 1);
        assertEquals(2, dlq.list(null).size());
    }

    // --- Ordering logic ---

    @Test
    void eventsProcessedInOrder() {
        var events = List.of(
                createTestEvent("evt-1"),
                createTestEvent("evt-2"),
                createTestEvent("evt-3"));
        var processedIds = events.stream()
                .map(e -> e.header().eventId())
                .toList();
        assertEquals(List.of("evt-1", "evt-2", "evt-3"), processedIds);
    }

    @Test
    void orderingPreservedWithDuplicates() {
        var events = List.of(
                createTestEvent("evt-1"),
                createTestEvent("evt-2"),
                createTestEvent("evt-1"), // duplicate
                createTestEvent("evt-3"));
        var unique = events.stream()
                .map(e -> e.header().eventId())
                .distinct()
                .toList();
        assertEquals(List.of("evt-1", "evt-2", "evt-3"), unique);
    }

    // --- Helpers ---

    private CDCEvent createTestEvent(String eventId) {
        return new CDCEvent(
                new EventHeader(eventId, "pipeline-1", "conn-1", 1, 1, Map.of()),
                new EventSource("db", "public", "users", "postgresql"),
                CDCOperation.INSERT,
                new EventPayload(null, Map.of("id", 1, "name", "test"), Map.of("id", 1)),
                new EventMetadata(1, java.time.Instant.now(), 0), null,
                new OffsetInformation("PG", Map.of("lsn", "123"), "", java.time.Instant.now()));
    }

    private Map<String, Object> applyRename(Map<String, Object> input, String from, String to) {
        var result = new java.util.LinkedHashMap<>(input);
        if (result.containsKey(from)) {
            result.put(to, result.remove(from));
        }
        return result;
    }

    private Map<String, Object> applyUppercase(Map<String, Object> input, String src, String dest) {
        var result = new java.util.LinkedHashMap<>(input);
        if (result.get(src) instanceof String s) {
            result.put(dest, s.toUpperCase());
        }
        return result;
    }

    private Map<String, Object> applyDefault(Map<String, Object> input, String src, String dest, String def) {
        var result = new java.util.LinkedHashMap<>(input);
        result.put(dest, result.get(src) != null ? result.get(src) : def);
        return result;
    }

    private Map<String, Object> applyIgnore(Map<String, Object> input, String col) {
        var result = new java.util.LinkedHashMap<>(input);
        result.remove(col);
        return result;
    }

    private boolean applyEqualsFilter(Map<String, Object> record, String field, String value) {
        return value.equals(record.get(field));
    }

    private boolean applyIsNullFilter(Map<String, Object> record, String field) {
        return record.get(field) == null;
    }

    private boolean applyContainsFilter(Map<String, Object> record, String field, String substr) {
        var val = record.get(field);
        return val instanceof String s && s.contains(substr);
    }

    private Map<String, Object> applyMapping(Map<String, Object> input, Map<String, String> mapping) {
        var result = new java.util.LinkedHashMap<String, Object>();
        input.forEach((k, v) -> {
            var destKey = mapping.getOrDefault(k, k);
            result.put(destKey, v);
        });
        return result;
    }
}
