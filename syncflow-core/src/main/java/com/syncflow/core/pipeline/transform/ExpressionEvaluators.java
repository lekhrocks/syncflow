package com.syncflow.core.pipeline.transform;

/**
 * Factories for {@link ExpressionEvaluator} instances supplied to core.
 *
 * <p>
 * {@code syncflow-core} ships no expression language of its own — the
 * production SpEL implementation lives in {@code syncflow-expression}. These
 * factories exist so core (and its tests) can obtain a deliberate, explicit
 * evaluator without depending on an adapter module.
 */
public final class ExpressionEvaluators {

    private ExpressionEvaluators() {
    }

    /**
     * An evaluator that fails loudly if an {@link TransformType#EXPRESSION} rule
     * is actually evaluated.
     *
     * <p>
     * This is the default used by {@code new TransformProcessor()}. It exists
     * so that a pipeline carrying an EXPRESSION rule but no wired evaluator
     * fails fast with a clear message, rather than silently passing the raw
     * value through and corrupting destination data. Non-EXPRESSION transforms
     * never invoke it and are unaffected.
     */
    public static ExpressionEvaluator unsupported() {
        return (expression, value, row) -> {
            throw new ExpressionEvaluationException(
                    "No ExpressionEvaluator is configured, but an EXPRESSION rule was"
                            + " encountered [expression=" + expression + "]. Wire a concrete"
                            + " evaluator (e.g. SpelExpressionEvaluator from syncflow-expression)"
                            + " into TransformProcessor.");
        };
    }
}
