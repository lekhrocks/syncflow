package com.syncflow.api.runtimestate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** JSON serialization for JSONB payloads in the runtime state tables. */
@Component
public class RuntimeStateJson {

    private final ObjectMapper objectMapper;

    public RuntimeStateJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize runtime state to JSON", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize runtime state from JSON: " + type.getSimpleName(),
                    e);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize runtime state from JSON", e);
        }
    }
}
