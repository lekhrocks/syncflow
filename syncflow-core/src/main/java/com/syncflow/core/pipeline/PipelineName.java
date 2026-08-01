package com.syncflow.core.pipeline;

public record PipelineName(String value) {

    public PipelineName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PipelineName must not be blank");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("PipelineName max 255 characters");
        }
    }
}
