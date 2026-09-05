package com.syncflow.core.pipeline.transform;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Evaluates SpEL column-level expressions for the
 * {@link TransformType#EXPRESSION} rule.
 *
 * <h3>Expression context</h3>
 * Two variables are bound for every evaluation:
 * <ul>
 * <li>{@code #value} — the current column value (may be {@code null})</li>
 * <li>{@code #row} — the full source row as a {@code Map<String, Object>};
 * access other columns with {@code #row['col_name']}</li>
 * </ul>
 *
 * <h3>Expression examples</h3>
 *
 * <pre>
 *   // Upper-case the current value
 *   #value?.toString().toUpperCase()
 *
 *   // Concatenate two columns
 *   #row['first_name'] + ' ' + #row['last_name']
 *
 *   // Conditional default
 *   #value != null ? #value : 'N/A'
 *
 *   // Arithmetic on a numeric column
 *   #value * 1.2
 *
 *   // Substring
 *   #value?.toString().substring(0, 3)
 * </pre>
 *
 * <h3>Security</h3>
 * Uses {@link SimpleEvaluationContext} with instance-method support, which
 * allows:
 * <ul>
 * <li>Property and index access on bound variables</li>
 * <li>Instance method calls on those values (e.g. {@code .toUpperCase()},
 * {@code .substring()})</li>
 * </ul>
 * The following remain blocked:
 * <ul>
 * <li>Static method calls ({@code T(System).exit(0)})</li>
 * <li>Class literal access ({@code T(java.io.File)})</li>
 * <li>Constructor invocation ({@code new java.io.File(...)})</li>
 * <li>Bean references ({@code @beanName})</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * {@link SpelExpressionParser} and {@link SimpleEvaluationContext} are
 * lightweight and
 * safe to construct per-call. The evaluator itself is stateless and can be
 * shared.
 */
public class SqlExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SqlExpressionEvaluator.class);

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Evaluates {@code expressionStr} against the given column value and full row.
     *
     * @param expressionStr
     *            the SpEL expression (e.g.
     *            {@code "#value?.toString().toUpperCase()"})
     * @param value
     *            the current column value; available as {@code #value}
     * @param row
     *            the full source row; available as {@code #row}
     * @return the evaluation result, or {@code null} if the expression evaluates to
     *         null
     * @throws ExpressionEvaluationException
     *             if the expression is syntactically invalid
     *             or throws during evaluation
     */
    public Object evaluate(String expressionStr, Object value, Map<String, Object> row) {
        if (expressionStr == null || expressionStr.isBlank()) {
            log.warn("EXPRESSION rule has blank expression string; returning value unchanged");
            return value;
        }

        try {
            var expression = PARSER.parseExpression(expressionStr);

            // SimpleEvaluationContext with instance method support:
            // - allows property/index access and instance method calls (.toUpperCase(),
            // .substring(), etc.)
            // - blocks static method calls, class literals (T(System)), and constructor
            // invocation (new ...)
            var context = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .build();
            context.setVariable("value", value);
            context.setVariable("row", row);

            return expression.getValue(context);

        } catch (ParseException e) {
            throw new ExpressionEvaluationException(
                    "Invalid SpEL expression [" + expressionStr + "]: " + e.getMessage(), e);
        } catch (EvaluationException e) {
            throw new ExpressionEvaluationException(
                    "Error evaluating expression [" + expressionStr + "] on value="
                            + value + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     * Unchecked exception thrown when a SpEL expression fails to parse or evaluate.
     * Wraps Spring's {@link ParseException} / {@link EvaluationException} so
     * callers
     * do not need a Spring dependency just to catch transformation errors.
     */
    public static class ExpressionEvaluationException extends RuntimeException {

        public ExpressionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
