package com.syncflow.api.ops.alert;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AlertEngine {

    private final Map<String, AlertEvent> alerts = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(0);

    public AlertEvent raise(String name, String message, AlertSeverity severity, String source) {
        return raise(name, message, severity, source, null, null);
    }

    public AlertEvent raise(String name, String message, AlertSeverity severity,
            String source, String pipelineId, String connectionId) {
        var id = "alert-" + counter.incrementAndGet();
        var event = new AlertEvent(id, name, message, severity, source,
                pipelineId, connectionId, java.time.Instant.now(), false);
        alerts.put(id, event);
        return event;
    }

    public void acknowledge(String id) {
        alerts.computeIfPresent(id, (k, v) -> new AlertEvent(v.id(), v.name(), v.message(), v.severity(),
                v.source(), v.pipelineId(), v.connectionId(),
                v.timestamp(), true));
    }

    public List<AlertEvent> active() {
        return alerts.values().stream()
                .filter(a -> !a.acknowledged())
                .sorted(Comparator.comparing(AlertEvent::timestamp).reversed())
                .toList();
    }

    public List<AlertEvent> all() {
        return alerts.values().stream()
                .sorted(Comparator.comparing(AlertEvent::timestamp).reversed())
                .limit(500)
                .toList();
    }

    public long count() {
        return alerts.size();
    }

    public void clearAcknowledged() {
        alerts.values().removeIf(AlertEvent::acknowledged);
    }
}
