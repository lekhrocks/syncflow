package com.syncflow.core.pipeline;

import java.util.UUID;

public record PipelineId(String value) {

    public PipelineId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PipelineId must not be blank");
        }
    }

    public static PipelineId generate() {
        return new PipelineId(UUID.randomUUID().toString());
    }

    public static PipelineId from(String value) {
        return new PipelineId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
