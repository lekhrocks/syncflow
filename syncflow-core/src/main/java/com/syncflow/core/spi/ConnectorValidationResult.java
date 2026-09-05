package com.syncflow.core.spi;

import java.util.List;

/**
 * Connector-SPI level validation result: returned by
 * {@link Connector#validate(ConnectorContext)} when a connector reports
 * whether it can accept a configuration. Distinct from
 * {@link com.syncflow.core.pipeline.validation.ValidationResult}, which
 * carries severity-graded issues for pipeline designers.
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
}
