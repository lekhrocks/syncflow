package com.syncflow.core.pipeline.preview;

public record PreviewFilter(
        String field,
        String operator,
        String value) {
}
