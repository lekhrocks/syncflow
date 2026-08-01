package com.syncflow.core.pipeline.preview;

public record PreviewColumn(
        String name,
        String sourceType,
        String destinationType,
        boolean transformed,
        boolean ignored) {
}
