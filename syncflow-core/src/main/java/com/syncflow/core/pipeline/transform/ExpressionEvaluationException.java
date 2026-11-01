package com.syncflow.core.pipeline.transform;

/**
 * Unchecked exception thrown when an {@link ExpressionEvaluator} fails to parse
 * or evaluate an expression.
 *
 * <p>
 * Lives in {@code syncflow-core} (framework-free) so that callers — and the
 * expression-language adapters themselves — can signal transformation failures
 * without depending on any expression-engine types. Concrete evaluators wrap
 * their native parse/evaluation errors as the {@link #getCause() cause}.
 */
public class ExpressionEvaluationException extends RuntimeException {

    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
