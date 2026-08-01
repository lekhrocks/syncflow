package com.syncflow.core.pipeline.mapping;

import java.util.List;

public record PrimaryKeyMapping(
        List<String> sourceColumns,
        List<String> destinationColumns) {
}
