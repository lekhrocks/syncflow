package com.syncflow.core.pipeline.transform;

import java.util.Map;

/**
 * Evaluates a column-level expression for a
 * {@link TransformType#EXPRESSION} transformation rule.
 *
 * <p>
 * This is a <em>port</em>: {@code syncflow-core} owns the abstraction and
 * never depends on a concrete expression language. The shipping
 * implementation is SpEL, provided by the {@code syncflow-expression} module
 * ({@code SpelExpressionEvaluator}), which is wired in at the composition
 * root. Swapping in another language (JSR-226, a sandboxed script engine,
 * etc.) requires no change to {@code syncflow-core}.
 *
 * <h3>Binding contract</h3>
 * Implementations must expose at least these two variables to the expression:
 * <ul>
 * <li>{@code #value} — the current column value (may be {@code null})</li>
 * <li>{@code #row} — the full source row as a {@code Map<String, Object>}</li>
 * </ul>
 *
 * <h3>Errors</h3>
 * Implementations must signal parse and evaluation failures by throwing
 * {@link ExpressionEvaluationException}, so callers can handle transformation
 * errors without depending on any expression-language types.
 */
public interface ExpressionEvaluator {

    /**
     * Evaluates {@code expression} against the given column value and row.
     *
     * @param expression
     *            the expression text (language-specific syntax)
     * @param value
     *            the current column value; available as {@code #value}
     * @param row
     *            the full source row; available as {@code #row}
     * @return the evaluation result, or {@code null} if it evaluates to null
     * @throws ExpressionEvaluationException
     *             if the expression is syntactically invalid or fails during
     *             evaluation
     */
    Object evaluate(String expression, Object value, Map<String, Object> row);
}
