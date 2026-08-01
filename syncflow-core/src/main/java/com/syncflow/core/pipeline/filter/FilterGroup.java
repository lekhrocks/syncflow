package com.syncflow.core.pipeline.filter;

import java.util.List;

public record FilterGroup(
        LogicalOperator operator,
        List<FilterCondition> conditions,
        List<FilterGroup> nestedGroups) {

    public enum LogicalOperator {
        AND, OR
    }

    public FilterGroup {
        conditions = List.copyOf(conditions == null ? List.of() : conditions);
        nestedGroups = List.copyOf(nestedGroups == null ? List.of() : nestedGroups);
    }

    public static FilterGroup all(List<FilterCondition> conditions) {
        return new FilterGroup(LogicalOperator.AND, conditions, List.of());
    }
}
