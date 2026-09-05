package com.syncflow.core.connection;

import com.syncflow.common.validation.ValidationResult;
import java.util.List;

/**
 * Connection validation result with database metrics.
 *
 * <p>
 * Thin adapter over {@link ValidationResult} in {@code syncflow-common}.
 * Extracts the connection-specific fields ({@code databaseVersion},
 * {@code driverName},
 * {@code latencyMs}) which are now part of the unified type.
 *
 * <p>
 * Callers that need the common type can call {@link #toCommon()}.
 */
public record DetailedValidationResult(
        boolean valid,
        String databaseVersion,
        String driverName,
        long latencyMs,
        List<String> errors) {

    public static DetailedValidationResult ok(String version, String driver, long latency) {
        return new DetailedValidationResult(true, version, driver, latency, List.of());
    }

    public static DetailedValidationResult failed(List<String> errors) {
        return new DetailedValidationResult(false, null, null, 0, List.copyOf(errors));
    }

    public DetailedValidationResult {
        errors = List.copyOf(errors == null ? List.of() : errors);
    }

    /**
     * Convert to the unified {@link ValidationResult} in {@code syncflow-common}.
     * Errors map to {@link ValidationResult.Severity#ERROR}.
     */
    public ValidationResult toCommon() {
        if (valid && errors.isEmpty()) {
            return ValidationResult.okWithMetrics(databaseVersion, driverName, latencyMs);
        }
        var issues = errors.stream()
                .map(e -> ValidationResult.Issue.error("CONNECTION_ERROR", e))
                .toList();
        return new ValidationResult(false, issues, databaseVersion, driverName, latencyMs);
    }
}
