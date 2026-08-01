package com.syncflow.core.pipeline.mapping;

import com.syncflow.core.pipeline.filter.FilterGroup;
import java.util.List;

public record TableMapping(
        String sourceTable,
        String destinationTable,
        String destinationCollection,
        PrimaryKeyMapping primaryKey,
        List<ColumnMapping> columnMappings,
        List<FieldMapping> fieldMappings,
        List<RelationshipMapping> relationships,
        FilterGroup filter) {

    public TableMapping {
        columnMappings = List.copyOf(columnMappings == null ? List.of() : columnMappings);
        fieldMappings = List.copyOf(fieldMappings == null ? List.of() : fieldMappings);
        relationships = List.copyOf(relationships == null ? List.of() : relationships);
    }
}
