package com.syncflow.api.sync;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventIdempotencyStore {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    public boolean isProcessed(String eventId) {
        return processed.contains(eventId);
    }

    public void markProcessed(String eventId) {
        processed.add(eventId);
    }

    public void evict(String eventId) {
        processed.remove(eventId);
    }

    public long size() {
        return processed.size();
    }
}
