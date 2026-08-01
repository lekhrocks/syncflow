package com.syncflow.core.pipeline.filter;

import java.util.List;

public record FilterCondition(
        String field,
        FilterOperator operator,
        List<String> values) {

    public FilterCondition {
        values = List.copyOf(values == null ? List.of() : values);
    }

    public static FilterCondition equals(String field, String value) {
        return new FilterCondition(field, FilterOperator.EQUALS, List.of(value));
    }

    public static FilterCondition isNull(String field) {
        return new FilterCondition(field, FilterOperator.IS_NULL, List.of());
    }
}
