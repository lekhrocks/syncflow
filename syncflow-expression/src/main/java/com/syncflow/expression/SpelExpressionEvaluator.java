package com.syncflow.expression;

import com.syncflow.core.pipeline.transform.ExpressionEvaluationException;
import com.syncflow.core.pipeline.transform.ExpressionEvaluator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * SpEL implementation of the {@link ExpressionEvaluator} port, backing the
 * {@link com.syncflow.core.pipeline.transform.TransformType#EXPRESSION} rule.
 *
 * <p>
 * Formerly {@code com.syncflow.core.pipeline.transform.SqlExpressionEvaluator}.
 * Moved out of {@code syncflow-core} so the domain layer stays free of any
 * expression-language dependency; {@code syncflow-core} now owns only the
 * {@link ExpressionEvaluator} abstraction.
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
 * lightweight and safe to construct per-call. The evaluator itself is
 * stateless and can be shared.
 */
public class SpelExpressionEvaluator implements ExpressionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SpelExpressionEvaluator.class);

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Evaluates {@code expression} against the given column value and full row.
     *
     * @param expression
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
    @Override
    public Object evaluate(String expression, Object value, Map<String, Object> row) {
        if (expression == null || expression.isBlank()) {
            log.warn("EXPRESSION rule has blank expression string; returning value unchanged");
            return value;
        }

        try {
            var parsed = PARSER.parseExpression(expression);

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

            return parsed.getValue(context);

        } catch (ParseException e) {
            throw new ExpressionEvaluationException(
                    "Invalid SpEL expression [" + expression + "]: " + e.getMessage(), e);
        } catch (EvaluationException e) {
            throw new ExpressionEvaluationException(
                    "Error evaluating expression [" + expression + "] on value="
                            + value + ": " + e.getMessage(),
                    e);
        }
    }
}
