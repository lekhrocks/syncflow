package com.syncflow.core.snapshot;

import com.syncflow.core.pipeline.PipelineDesign;
import java.util.Map;

public record ExecutionContext(
        PipelineDesign pipeline,
        Map<String, Object> runtimeProperties) {

    public ExecutionContext {
        runtimeProperties = Map.copyOf(runtimeProperties == null ? Map.of() : runtimeProperties);
    }
}
