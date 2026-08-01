package com.syncflow.core.pipeline;

import java.util.Map;

public record PipelineSettings(
        SyncMode syncMode,
        int batchSize,
        int maxRetries,
        boolean skipConstraints,
        boolean skipIndexes,
        Map<String, String> properties) {

    public PipelineSettings {
        if (batchSize <= 0)
            batchSize = 1000;
        if (maxRetries < 0)
            maxRetries = 3;
        properties = Map.copyOf(properties == null ? Map.of() : properties);
    }

    public static PipelineSettings defaults() {
        return new PipelineSettings(SyncMode.FULL_SNAPSHOT, 1000, 3, false, false, Map.of());
    }
}
