package com.syncflow.core.spi;

import java.util.List;

/**
 * Connector-SPI level validation result: returned by
 * {@link Connector#validate(ConnectorContext)} when a connector reports
 * whether it can accept a configuration. Distinct from
 * {@link com.syncflow.core.pipeline.validation.ValidationResult}, which
 * carries severity-graded issues for pipeline designers. The two are NOT
 * interchangeable — keep this one for SPI responses, the other for
 * pipeline validation.
 */
public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings) {

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult failed(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
    }

    public ValidationResult {
        errors = List.copyOf(errors == null ? List.of() : errors);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
