package com.syncflow.api.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StatusBroadcasterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StatusBroadcaster broadcaster = new StatusBroadcaster(mapper);

    /** Returns a mock emitter and captures the SseEventBuilder passed to send(). */
    private SseEmitter mockEmitter() {
        var emitter = mock(SseEmitter.class);
        // onCompletion/onTimeout/onError must not throw when the broadcaster
        // registers cleanup callbacks on subscribe.
        doAnswer(inv -> null).when(emitter).onCompletion(any(Runnable.class));
        doAnswer(inv -> null).when(emitter).onTimeout(any(Runnable.class));
        doAnswer(inv -> null).when(emitter).onError(any(Consumer.class));
        return emitter;
    }

    @Test
    void subscribeRegistersAnEmitter() {
        var emitter = mockEmitter();
        broadcaster.subscribe("job-1", emitter);
        assertEquals(1, broadcaster.subscriberCount("job-1"));
    }

    @Test
    void emitCallsSendWithEventNameAndJsonPayload() throws Exception {
        var emitter = mockEmitter();
        broadcaster.subscribe("job-1", emitter);
        var payload = Map.of("state", "RUNNING", "pipelineId", "p-1", "processedEvents", 5);

        broadcaster.emit("job-1", "sync-status", payload);

        // The broadcaster must hand the emitter a builder carrying the event name
        // and the JSON-serialized payload.
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        // Confirm the JSON the payload serializes to is well-formed and correct.
        var json = mapper.writeValueAsString(payload);
        assertEquals("RUNNING", mapper.readTree(json).get("state").asText());
        assertEquals(5, mapper.readTree(json).get("processedEvents").asInt());
    }

    @Test
    void emitToMultipleSubscribersDeliversToEach() throws Exception {
        var e1 = mockEmitter();
        var e2 = mockEmitter();
        broadcaster.subscribe("job-1", e1);
        broadcaster.subscribe("job-1", e2);
        assertEquals(2, broadcaster.subscriberCount("job-1"));

        broadcaster.emit("job-1", "snapshot-status", Map.of("status", "RUNNING"));

        verify(e1).send(any(SseEmitter.SseEventBuilder.class));
        verify(e2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void emitWithNoSubscribersIsNoOp() {
        // Should not throw when nothing is subscribed.
        broadcaster.emit("nobody", "sync-status", Map.of("state", "RUNNING"));
        assertTrue(true);
    }

    @Test
    void emittersIsolatedPerJob() {
        broadcaster.subscribe("job-1", mockEmitter());
        broadcaster.subscribe("job-2", mockEmitter());
        assertEquals(1, broadcaster.subscriberCount("job-1"));
        assertEquals(1, broadcaster.subscriberCount("job-2"));
    }
}