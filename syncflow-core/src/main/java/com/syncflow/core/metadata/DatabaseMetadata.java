package com.syncflow.core.metadata;

import java.util.List;

public record DatabaseMetadata(
        String databaseName,
        String databaseVersion,
        String driverName,
        List<SchemaMetadata> schemas) {
}
