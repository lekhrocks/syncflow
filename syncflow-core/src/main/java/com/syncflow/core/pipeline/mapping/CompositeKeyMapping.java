package com.syncflow.core.pipeline.mapping;

import java.util.List;

public record CompositeKeyMapping(
        String compositeKeyName,
        List<String> sourceColumns,
        List<String> destinationColumns) {
}
