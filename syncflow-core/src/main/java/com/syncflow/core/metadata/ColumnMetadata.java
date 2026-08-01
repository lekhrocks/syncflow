package com.syncflow.core.metadata;

public record ColumnMetadata(
        String name,
        int ordinalPosition,
        DataType dataType,
        boolean primaryKey,
        boolean foreignKey,
        boolean unique,
        boolean autoIncrement,
        String comment) {
}
