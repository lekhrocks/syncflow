package com.syncflow.core.metadata;

import java.util.List;

public record ConstraintMetadata(
        String name,
        String type,
        String definition,
        List<String> columnNames) {
}
