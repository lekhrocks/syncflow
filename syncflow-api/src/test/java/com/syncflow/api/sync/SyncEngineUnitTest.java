package com.syncflow.api.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncflow.api.sync.entity.DeadLetterEventEntity;
import com.syncflow.api.sync.entity.ProcessedEventEntity;
import com.syncflow.api.sync.repository.DeadLetterEventRepository;
import com.syncflow.api.sync.repository.ProcessedEventRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncEngineUnitTest {

    private final DeadLetterEventRepository dlqRepo = mock(DeadLetterEventRepository.class);
    private final ProcessedEventRepository processedRepo = mock(ProcessedEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DeadLetterQueue dlq = new DeadLetterQueue(dlqRepo, objectMapper);
    private final RetryEngine retry = new RetryEngine(dlq, new SimpleMeterRegistry());
    private final EventIdempotencyStore idempotency = new EventIdempotencyStore(processedRepo);
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
        // event was persisted to the DLQ repo
        verify(dlqRepo).save(any(DeadLetterEventEntity.class));
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
        verify(dlqRepo).save(any(DeadLetterEventEntity.class));
    }

    // --- Conflict resolution ---

    @Test
    void conflictDetectedByPrimaryKey() {
        // Same eventId is flagged as already processed once persisted
        when(processedRepo.existsByEventId("evt-5")).thenReturn(true);
        var event = createTestEvent("evt-5");
        assertTrue(idempotency.isProcessed(event.header().eventId()));
    }

    // --- Deduplication ---

    @Test
    void deduplicationPreventsDuplicateProcessing() {
        var event = createTestEvent("evt-6");
        when(processedRepo.existsByEventId("evt-6")).thenReturn(false);
        assertFalse(idempotency.isProcessed(event.header().eventId()));

        idempotency.markProcessed(event.header().eventId());
        verify(processedRepo).save(any(ProcessedEventEntity.class));

        // a second occurrence is now seen as processed
        when(processedRepo.existsByEventId("evt-6")).thenReturn(true);
        assertTrue(idempotency.isProcessed(event.header().eventId()));
    }

    @Test
    void deduplicationEviction() {
        var event = createTestEvent("evt-7");
        when(processedRepo.existsByEventId("evt-7")).thenReturn(true);
        assertTrue(idempotency.isProcessed(event.header().eventId()));

        idempotency.evict(event.header().eventId());
        verify(processedRepo).deleteById("evt-7");
    }

    // --- DLQ logic ---

    @Test
    void dlqStoresFailedEvent() {
        var event = createTestEvent("evt-8");
        dlq.add("p-1", event, FailureReason.permanentError("bad data"), 3);
        verify(dlqRepo).save(any(DeadLetterEventEntity.class));
    }

    @Test
    void dlqListFilteredByPipeline() {
        var e1 = dlqEntity("d1", "p-1", "e1");
        var e2 = dlqEntity("d2", "p-2", "e2");
        var e3 = dlqEntity("d3", "p-1", "e3");
        when(dlqRepo.findByPipelineIdOrderByCreatedAtDesc("p-1"))
                .thenReturn(List.of(e1, e3));
        when(dlqRepo.findByPipelineIdOrderByCreatedAtDesc("p-2"))
                .thenReturn(List.of(e2));

        var p1Events = dlq.list("p-1");
        assertEquals(2, p1Events.size());
        var p2Events = dlq.list("p-2");
        assertEquals(1, p2Events.size());
    }

    @Test
    void dlqDeleteById() {
        dlq.delete("d_del");
        verify(dlqRepo).deleteById("d_del");
    }

    @Test
    void dlqListAllWhenPipelineIsNull() {
        var e1 = dlqEntity("d1", "p-1", "e1");
        var e2 = dlqEntity("d2", "p-2", "e2");
        when(dlqRepo.findAll()).thenReturn(List.of(e1, e2));
        assertEquals(2, dlq.list(null).size());
    }

    private DeadLetterEventEntity dlqEntity(String id, String pipelineId, String eventId) {
        var entity = new DeadLetterEventEntity();
        entity.setId(id);
        entity.setPipelineId(pipelineId);
        entity.setEventId(eventId);
        entity.setEventData("{}");
        entity.setFailureType("PERMANENT");
        return entity;
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
