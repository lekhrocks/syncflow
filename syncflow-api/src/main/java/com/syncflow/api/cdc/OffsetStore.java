package com.syncflow.api.cdc;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OffsetStore {

    private final Map<String, Map<String, String>> offsets = new ConcurrentHashMap<>();

    public void save(String pipelineId, Map<String, String> offset) {
        offsets.put(pipelineId, Map.copyOf(offset));
    }

    public Map<String, String> get(String pipelineId) {
        return offsets.getOrDefault(pipelineId, Map.of());
    }

    public void delete(String pipelineId) {
        offsets.remove(pipelineId);
    }
}
