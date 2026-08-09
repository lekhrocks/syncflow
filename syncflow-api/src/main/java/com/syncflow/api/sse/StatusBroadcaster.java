package com.syncflow.api.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out of live status events to SSE subscribers, keyed by job id.
 * Emitters are cleaned up on completion, timeout, or error. Thread-safe:
 * SnapshotExecutor / SyncOrchestrator emit from their worker threads while
 * HTTP request threads subscribe.
 */
@Component
public class StatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StatusBroadcaster.class);
    private static final long DEFAULT_TIMEOUT_MS = 0; // no server timeout; client-driven

    private final ObjectMapper objectMapper;
    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public StatusBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Subscribe an emitter to a job's event stream. */
    public SseEmitter subscribe(String jobId) {
        return subscribe(jobId, new SseEmitter(DEFAULT_TIMEOUT_MS));
    }

    /** Package-private seam for tests: register a provided emitter. */
    SseEmitter subscribe(String jobId, SseEmitter emitter) {
        var list = subscribers.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(e -> remove(jobId, emitter));
        return emitter;
    }

    /** Broadcast an event to all subscribers of a job. */
    public void emit(String jobId, String eventName, Object payload) {
        var list = subscribers.get(jobId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE payload for job={}", jobId, e);
            return;
        }
        for (var emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException | IllegalStateException e) {
                // Client disconnected — drop and clean up.
                remove(jobId, emitter);
            }
        }
    }

    /** Number of active subscribers for a job (for tests). */
    public int subscriberCount(String jobId) {
        return subscribers.getOrDefault(jobId, List.of()).size();
    }

    private void remove(String jobId, SseEmitter emitter) {
        var list = subscribers.get(jobId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(jobId);
            }
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
