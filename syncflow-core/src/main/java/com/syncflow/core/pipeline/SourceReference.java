package com.syncflow.core.pipeline;

public record SourceReference(
        String connectionId,
        String schema,
        String tableOrCollection) {
}
