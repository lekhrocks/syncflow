package com.syncflow.core.metadata;

import java.util.List;

public record IndexMetadata(
        String name,
        List<String> columnNames,
        boolean unique,
        boolean primary,
        String indexType) {
}
