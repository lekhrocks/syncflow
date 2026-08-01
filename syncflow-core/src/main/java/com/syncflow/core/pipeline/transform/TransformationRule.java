package com.syncflow.core.pipeline.transform;

import java.util.List;
import java.util.Map;

public record TransformationRule(
        TransformType type,
        Map<String, String> parameters,
        List<String> sourceFields) {

    public TransformationRule {
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
        sourceFields = List.copyOf(sourceFields == null ? List.of() : sourceFields);
    }

    public static TransformationRule rename(String newName) {
        return new TransformationRule(TransformType.RENAME,
                Map.of("newName", newName), List.of());
    }

    public static TransformationRule ignore() {
        return new TransformationRule(TransformType.IGNORE, Map.of(), List.of());
    }

    public static TransformationRule constant(String value) {
        return new TransformationRule(TransformType.CONSTANT_VALUE,
                Map.of("value", value), List.of());
    }

    public static TransformationRule concat(List<String> fields, String separator) {
        return new TransformationRule(TransformType.CONCATENATE,
                Map.of("separator", separator), fields);
    }

    public static TransformationRule uppercase() {
        return new TransformationRule(TransformType.UPPERCASE, Map.of(), List.of());
    }

    public static TransformationRule lowercase() {
        return new TransformationRule(TransformType.LOWERCASE, Map.of(), List.of());
    }

    public static TransformationRule trim() {
        return new TransformationRule(TransformType.TRIM, Map.of(), List.of());
    }

    public static TransformationRule defaultValue(String value) {
        return new TransformationRule(TransformType.DEFAULT_VALUE,
                Map.of("value", value), List.of());
    }

    public static TransformationRule expression(String expression) {
        return new TransformationRule(TransformType.EXPRESSION,
                Map.of("expression", expression), List.of());
    }
}
