package com.syncflow.api.pipeline.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.TransformationConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Jackson-based JSON conversion helper used by MapStruct mappers via
 * {@code uses}.
 * Each method is named to match the target field type so MapStruct can resolve
 * them.
 */
@Component
public class JsonMapper {

    private final ObjectMapper objectMapper;

    public JsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Serializers (domain → JSON string) ──────────────────────────────────

    public String fromSourceReference(SourceReference value) {
        return toJson(value);
    }

    public String fromDestinationReference(DestinationReference value) {
        return toJson(value);
    }

    public String fromTableMappings(List<TableMapping> value) {
        return toJson(value);
    }

    public String fromPipelineSettings(PipelineSettings value) {
        return toJson(value);
    }

    public String fromConnectionConfiguration(ConnectionConfiguration value) {
        return toJson(value);
    }

    public String fromTransformationConfiguration(TransformationConfiguration value) {
        return value != null ? toJson(value) : null;
    }

    // ── Deserializers (JSON string → domain) ────────────────────────────────

    public SourceReference toSourceReference(String json) {
        return fromJson(json, SourceReference.class);
    }

    public DestinationReference toDestinationReference(String json) {
        return fromJson(json, DestinationReference.class);
    }

    public List<TableMapping> toTableMappings(String json) {
        return fromJson(json, new TypeReference<List<TableMapping>>() {
        });
    }

    public PipelineSettings toPipelineSettings(String json) {
        return fromJson(json, PipelineSettings.class);
    }

    public ConnectionConfiguration toConnectionConfiguration(String json) {
        return fromJson(json, ConnectionConfiguration.class);
    }

    public TransformationConfiguration toTransformationConfiguration(String json) {
        return json != null ? fromJson(json, TransformationConfiguration.class) : null;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize to JSON", ex);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize from JSON: " + type.getSimpleName(), ex);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize from JSON", ex);
        }
    }
}
