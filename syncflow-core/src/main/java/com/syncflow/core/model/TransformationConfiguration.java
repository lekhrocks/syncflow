package com.syncflow.core.model;

import java.util.List;
import java.util.Map;

public record TransformationConfiguration(
        List<String> includedTables,
        List<String> excludedTables,
        Map<String, String> columnMappings,
        Map<String, String> expressionMappings) {

    public TransformationConfiguration {
        includedTables = List.copyOf(includedTables == null ? List.of() : includedTables);
        excludedTables = List.copyOf(excludedTables == null ? List.of() : excludedTables);
        columnMappings = Map.copyOf(columnMappings == null ? Map.of() : columnMappings);
        expressionMappings = Map.copyOf(expressionMappings == null ? Map.of() : expressionMappings);
    }
}
