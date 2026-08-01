package com.syncflow.core.metadata;

import java.util.List;

public record SchemaMetadata(
        String name,
        List<TableMetadata> tables) {
}
