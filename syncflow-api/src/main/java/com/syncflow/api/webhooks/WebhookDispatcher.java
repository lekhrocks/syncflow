package com.syncflow.api.webhooks;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class WebhookDispatcher {

    private final Map<String, List<Consumer<WebhookEvent>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, WebhookEvent> eventLog = new ConcurrentHashMap<>();

    public void subscribe(String eventType, Consumer<WebhookEvent> handler) {
        listeners.computeIfAbsent(eventType, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
    }

    public void dispatch(WebhookEvent event) {
        var handlers = listeners.getOrDefault(event.type(), List.of());
        handlers.forEach(h -> {
            try {
                h.accept(event);
            } catch (Exception ignored) {
            }
        });
        eventLog.put(event.id(), event);
    }

    public List<WebhookEvent> recent(int limit) {
        return eventLog.values().stream()
                .sorted(Comparator.comparing(WebhookEvent::timestamp).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }
}
