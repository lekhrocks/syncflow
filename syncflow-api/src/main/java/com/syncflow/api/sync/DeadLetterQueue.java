package com.syncflow.api.sync;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.sync.FailureReason;
import com.syncflow.core.sync.dlq.DeadLetterEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeadLetterQueue {

    private final Map<String, DeadLetterEvent> store = new ConcurrentHashMap<>();

    public void add(String pipelineId, CDCEvent event, FailureReason reason, int retryCount) {
        var dle = new DeadLetterEvent(UUID.randomUUID().toString(), pipelineId,
                event, reason, retryCount, Instant.now());
        store.put(dle.id(), dle);
    }

    public DeadLetterEvent get(String id) {
        return store.get(id);
    }

    public List<DeadLetterEvent> list(String pipelineId) {
        return store.values().stream()
                .filter(d -> pipelineId == null || d.pipelineId().equals(pipelineId))
                .toList();
    }

    public void delete(String id) {
        store.remove(id);
    }

    public void replay(String id) {
        store.remove(id);
    }

    public long count() {
        return store.size();
    }
}
