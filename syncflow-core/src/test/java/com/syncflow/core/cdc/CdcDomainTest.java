package com.syncflow.core.cdc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CdcDomainTest {

    @Test
    void createCDCEventWithInsert() {
        var header = new EventHeader("evt-1", "pipeline-1", "conn-1", 1, 1, Map.of());
        var source = new EventSource("mydb", "public", "users", "postgresql");
        var payload = new EventPayload(null, Map.of("id", 1, "name", "Alice"), Map.of("id", 1));
        var event = new CDCEvent(header, source, CDCOperation.INSERT, payload,
                new EventMetadata(1, java.time.Instant.now(), 0), null,
                new OffsetInformation("POSTGRESQL", Map.of("lsn", "123"), "", java.time.Instant.now()));

        assertEquals("evt-1", event.header().eventId());
        assertEquals(CDCOperation.INSERT, event.operation());
        assertEquals("users", event.source().table());
    }

    @Test
    void createCDCEventWithDelete() {
        var header = new EventHeader("evt-2", "p-1", "c-1", 1, 1, Map.of());
        var payload = new EventPayload(Map.of("id", 1, "name", "Bob"), null, Map.of("id", 1));
        var event = new CDCEvent(header, new EventSource("db", "public", "users", "pg"),
                CDCOperation.DELETE, payload, new EventMetadata(1, java.time.Instant.now(), 0), null,
                new OffsetInformation("POSTGRESQL", Map.of(), "", java.time.Instant.now()));

        assertEquals(CDCOperation.DELETE, event.operation());
        assertEquals("Bob", event.payload().before().get("name"));
        assertNull(event.payload().after());
    }

    @Test
    void offsetInformationImmutability() {
        var offset = new OffsetInformation("PG", Map.of("lsn", "456"), "tx-1", java.time.Instant.now());
        assertEquals("456", offset.offset().get("lsn"));
        assertEquals("PG", offset.connectorType());
    }

    @Test
    void eventPayloadImmutability() {
        var mutable = new java.util.HashMap<String, Object>(Map.of("id", 1));
        var payload = new EventPayload(null, mutable, Map.of());
        mutable.put("id", 2);
        assertEquals(1, payload.after().get("id"));
    }

    @Test
    void captureStatusValues() {
        assertNotNull(CaptureStatus.valueOf("INACTIVE"));
        assertNotNull(CaptureStatus.valueOf("RUNNING"));
        assertNotNull(CaptureStatus.valueOf("PAUSED"));
        assertNotNull(CaptureStatus.valueOf("FAILED"));
    }

    @Test
    void cdcOperationValues() {
        assertNotNull(CDCOperation.valueOf("INSERT"));
        assertNotNull(CDCOperation.valueOf("UPDATE"));
        assertNotNull(CDCOperation.valueOf("DELETE"));
    }

    @Test
    void eventHeaderCustomProperties() {
        var h = new EventHeader("id", "p", "c", 1, 1, Map.of("key", "val"));
        assertEquals("val", h.customProperties().get("key"));
    }
}
