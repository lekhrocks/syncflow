package com.syncflow.core.snapshot.pipeline;

import com.syncflow.core.pipeline.filter.FilterCondition;
import com.syncflow.core.pipeline.filter.FilterGroup;
import com.syncflow.core.pipeline.filter.FilterOperator;
import java.util.Map;
import java.util.stream.Stream;

public class FilterProcessor implements RecordProcessor {

    @Override
    public Map<String, Object> process(Map<String, Object> record, ProcessingContext ctx) {
        var filter = ctx.tableMapping().filter();
        if (filter == null)
            return record;
        return evaluateGroup(filter, record) ? record : null;
    }

    public static Stream<Map<String, Object>> apply(FilterGroup filter, Stream<Map<String, Object>> rows) {
        if (filter == null)
            return rows;
        return rows.filter(r -> evaluateGroup(filter, r));
    }

    private static boolean evaluateGroup(FilterGroup group, Map<String, Object> record) {
        if (group == null)
            return true;
        var conditions = group.conditions().stream()
                .map(c -> evaluateCondition(c, record));
        var nested = group.nestedGroups().stream()
                .map(g -> evaluateGroup(g, record));

        return group.operator() == FilterGroup.LogicalOperator.AND
                ? Stream.concat(conditions, nested).allMatch(b -> b)
                : Stream.concat(conditions, nested).anyMatch(b -> b);
    }

    private static boolean evaluateCondition(FilterCondition cond, Map<String, Object> record) {
        var value = record.get(cond.field());
        if (value == null) {
            return cond.operator() == FilterOperator.IS_NULL;
        }
        return switch (cond.operator()) {
            case EQUALS, IN -> cond.values().contains(value.toString());
            case NOT_EQUALS, NOT_IN -> !cond.values().contains(value.toString());
            case GREATER_THAN -> compare(value, cond.values().getFirst()) > 0;
            case LESS_THAN -> compare(value, cond.values().getFirst()) < 0;
            case CONTAINS -> value.toString().contains(cond.values().getFirst());
            case STARTS_WITH -> value.toString().startsWith(cond.values().getFirst());
            case ENDS_WITH -> value.toString().endsWith(cond.values().getFirst());
            case IS_NULL -> false;
            case IS_NOT_NULL -> true;
        };
    }

    private static int compare(Object a, String b) {
        if (a instanceof Number n) {
            return Double.compare(n.doubleValue(), Double.parseDouble(b));
        }
        return a.toString().compareTo(b);
    }
}
