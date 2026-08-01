package com.syncflow.core.pipeline.preview;

import java.util.List;

public record PipelinePreview(
        String sourceTable,
        String destinationTable,
        List<PreviewColumn> sourceColumns,
        List<PreviewColumn> destinationColumns,
        List<PreviewTransformation> appliedTransformations,
        List<PreviewFilter> appliedFilters,
        int estimatedColumnCount) {
}
