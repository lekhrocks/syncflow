package com.syncflow.core.snapshot.pipeline;

import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.mapping.TableMapping;

public record ProcessingContext(
        PipelineDesign pipeline,
        TableMapping tableMapping) {
}
