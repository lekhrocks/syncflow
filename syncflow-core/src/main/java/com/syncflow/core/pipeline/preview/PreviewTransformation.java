package com.syncflow.core.pipeline.preview;

public record PreviewTransformation(
        String sourceColumn,
        String destinationColumn,
        String transformType,
        String summary) {
}
