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
        FilterGroup filter,
        /**
         * Optional whole-row SQL projection queries (Phase 2 / F19).
         * Each string is a {@code SELECT} statement whose {@code FROM} clause
         * must reference the virtual table {@code __row__}. Queries are applied
         * in order before column-level
         * {@link com.syncflow.core.pipeline.transform.TransformType#EXPRESSION}
         * rules run.
         *
         * <p>
         * Example:
         *
         * <pre>{@code
         *   SELECT id,
         *          UPPER(first_name) AS first_name,
         *          price * 1.2      AS adjusted_price
         *   FROM __row__
         * }</pre>
         */
        List<String> sqlTransforms) {

    public TableMapping {
        columnMappings = List.copyOf(columnMappings == null ? List.of() : columnMappings);
        fieldMappings = List.copyOf(fieldMappings == null ? List.of() : fieldMappings);
        relationships = List.copyOf(relationships == null ? List.of() : relationships);
        sqlTransforms = List.copyOf(sqlTransforms == null ? List.of() : sqlTransforms);
    }

    /**
     * Convenience constructor for callers that do not use SQL row transforms
     * (preserves backward compatibility with all existing 8-arg call sites).
     */
    public TableMapping(
            String sourceTable,
            String destinationTable,
            String destinationCollection,
            PrimaryKeyMapping primaryKey,
            List<ColumnMapping> columnMappings,
            List<FieldMapping> fieldMappings,
            List<RelationshipMapping> relationships,
            FilterGroup filter) {
        this(sourceTable, destinationTable, destinationCollection, primaryKey,
                columnMappings, fieldMappings, relationships, filter, List.of());
    }
}
