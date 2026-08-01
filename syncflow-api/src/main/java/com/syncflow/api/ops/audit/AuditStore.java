package com.syncflow.api.ops.audit;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuditStore {

    private final Map<String, AuditEvent> store = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(0);

    public void record(String action, String entityType, String entityId,
            String details, String correlationId, boolean success) {
        var id = "audit-" + counter.incrementAndGet();
        store.put(id, new AuditEvent(id, action, entityType, entityId, details,
                correlationId, "system", success, java.time.Instant.now()));
    }

    public List<AuditEvent> list(String entityType, String entityId, int limit) {
        return store.values().stream()
                .filter(e -> entityType == null || e.entityType().equals(entityType))
                .filter(e -> entityId == null || e.entityId().equals(entityId))
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .limit(limit > 0 ? limit : 100)
                .toList();
    }

    public long count() {
        return store.size();
    }
}
