package com.syncflow.core.metadata;

import java.util.List;

public record TableMetadata(
        String name,
        String type,
        String schema,
        String comment,
        TableStatistics statistics,
        List<ColumnMetadata> columns,
        List<IndexMetadata> indexes,
        PrimaryKeyMetadata primaryKey,
        List<ForeignKeyMetadata> foreignKeys,
        List<ConstraintMetadata> constraints) {
}
