package com.syncflow.core.spi;

import com.syncflow.common.validation.ValidationResult;

import java.util.List;

/**
 * Connector-SPI level validation result: returned by
 * {@link Connector#validate(ConnectorContext)} when a connector reports
 * whether it can accept a configuration.
 *
 * <p>
 * Thin adapter over {@link com.syncflow.common.validation.ValidationResult}
 * so the {@link Connector} SPI signature is stable while the shared type lives
 * in {@code syncflow-common}. All callers that need the common type can call
 * {@link #toValidationResult()}.
 */
public record ConnectorValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings) {

    public static ConnectorValidationResult ok() {
        return new ConnectorValidationResult(true, List.of(), List.of());
    }

    public static ConnectorValidationResult failed(List<String> errors) {
        return new ConnectorValidationResult(false, errors, List.of());
    }

    public ConnectorValidationResult {
        errors = List.copyOf(errors == null ? List.of() : errors);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    /**
     * Convert to the unified {@link ValidationResult} in {@code syncflow-common}.
     * Errors map to {@link ValidationResult.Severity#ERROR}; warnings to
     * {@link ValidationResult.Severity#WARNING}.
     */
    public ValidationResult toValidationResult() {
        if (valid && errors.isEmpty() && warnings.isEmpty()) {
            return ValidationResult.ok();
        }
        var builder = ValidationResult.Builder.failed();
        errors.forEach(e -> builder.error("CONNECTOR_ERROR", e));
        warnings.forEach(w -> builder.warning("CONNECTOR_WARNING", w));
        return builder.build();
    }
}
