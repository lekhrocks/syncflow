package com.syncflow.core.snapshot.pipeline;

import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.transform.ExpressionEvaluator;
import com.syncflow.core.pipeline.transform.ExpressionEvaluators;
import com.syncflow.core.pipeline.transform.TransformType;
import com.syncflow.core.pipeline.transform.TransformationRule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Applies per-column transformation rules to a record.
 *
 * <p>
 * Column expressions are delegated to an {@link ExpressionEvaluator} port
 * rather than a concrete language, keeping this class — and
 * {@code syncflow-core} — free of any expression-engine dependency. Wire a
 * concrete evaluator (e.g. {@code SpelExpressionEvaluator} from
 * {@code syncflow-expression}) via the constructor when EXPRESSION rules are
 * in use; the no-arg constructor deliberately fails on EXPRESSION rules so
 * misconfiguration is loud instead of silently dropping the transform.
 */
public class TransformProcessor implements RecordProcessor {

    private final ExpressionEvaluator evaluator;

    /**
     * Uses an evaluator that fails loudly if an EXPRESSION rule is evaluated.
     * Safe for pipelines that use only non-EXPRESSION rules (RENAME, UPPERCASE,
     * SUBSTRING, ...); those never invoke the evaluator.
     */
    public TransformProcessor() {
        this(ExpressionEvaluators.unsupported());
    }

    /**
     * @param evaluator
     *            the expression engine used for {@link TransformType#EXPRESSION}
     *            rules; must not be {@code null}
     */
    public TransformProcessor(ExpressionEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    }

    @Override
    public Map<String, Object> process(Map<String, Object> record, ProcessingContext ctx) {
        var result = new LinkedHashMap<String, Object>();

        for (var mapping : ctx.tableMapping().columnMappings()) {
            if (isIgnored(mapping))
                continue;
            var value = applyTransformations(mapping, record);
            result.put(mapping.destinationColumn(), value);
        }

        // ponytail: passthrough unmapped columns to avoid silent data loss
        for (var entry : record.entrySet()) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
        }

        return result;
    }

    private boolean isIgnored(ColumnMapping cm) {
        return cm.transformations().stream()
                .anyMatch(t -> t.type() == TransformType.IGNORE);
    }

    private Object applyTransformations(ColumnMapping mapping, Map<String, Object> record) {
        var value = record.get(mapping.sourceColumn());
        for (var rule : mapping.transformations()) {
            value = apply(rule, value, record);
        }
        return value;
    }

    private Object apply(TransformationRule rule, Object value, Map<String, Object> record) {
        return switch (rule.type()) {
            case RENAME -> value; // handled by the destination column name
            case CONSTANT_VALUE -> rule.parameters().get("value");
            case CONCATENATE -> rule.sourceFields().stream()
                    .map(f -> Objects.toString(record.get(f), ""))
                    .collect(Collectors.joining(rule.parameters().getOrDefault("separator", "")));
            case SUBSTRING -> {
                var s = Objects.toString(value, "");
                var start = Integer.parseInt(rule.parameters().getOrDefault("start", "0"));
                var end = rule.parameters().containsKey("end")
                        ? Integer.parseInt(rule.parameters().get("end"))
                        : s.length();
                yield s.substring(Math.min(start, s.length()), Math.min(end, s.length()));
            }
            case UPPERCASE -> Objects.toString(value, "").toUpperCase();
            case LOWERCASE -> Objects.toString(value, "").toLowerCase();
            case TRIM -> Objects.toString(value, "").trim();
            case DEFAULT_VALUE -> value != null ? value : rule.parameters().get("value");
            case EXPRESSION -> {
                var expr = rule.parameters().get("expression");
                yield evaluator.evaluate(expr, value, record);
            }
            case IGNORE -> value;
            // SQL_QUERY operates at the whole-row level in SqlRowTransformProcessor
            // and is not applied per-column; pass the value through unchanged.
            case SQL_QUERY -> value;
        };
    }
}
