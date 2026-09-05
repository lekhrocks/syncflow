package com.syncflow.common.validation;

import java.util.List;

/**
 * Unified validation result used across the application.
 * <p>
 * This single type consolidates:
 * <ul>
 * <li>{@link com.syncflow.core.spi.ConnectorValidationResult} - connector-SPI
 * validation</li>
 * <li>{@link com.syncflow.core.pipeline.validation.ValidationResult} - pipeline
 * design validation</li>
 * <li>{@link com.syncflow.core.connection.DetailedValidationResult} -
 * connection validation with metrics</li>
 * </ul>
 * <p>
 * Connection-test specific fields ({@code databaseVersion}, {@code driverName},
 * {@code latencyMs})
 * are optional and present only when validation includes a live connection
 * probe.
 * <p>
 * Use {@link Builder} to construct instances with appropriate severity grading.
 */
public record ValidationResult(
        boolean valid,
        List<Issue> issues,
        String databaseVersion,
        String driverName,
        long latencyMs) {

    /**
     * Constructor for non-connection validation (no metrics).
     */
    public ValidationResult(boolean valid, List<Issue> issues) {
        this(valid, issues, null, null, 0);
    }

    /**
     * An individual validation issue with a severity level.
     */
    public record Issue(
            String code,
            String message,
            Severity severity,
            String field) {

        public static Issue error(String code, String message) {
            return new Issue(code, message, Severity.ERROR, null);
        }

        public static Issue error(String code, String message, String field) {
            return new Issue(code, message, Severity.ERROR, field);
        }

        public static Issue warning(String code, String message) {
            return new Issue(code, message, Severity.WARNING, null);
        }

        public static Issue warning(String code, String message, String field) {
            return new Issue(code, message, Severity.WARNING, field);
        }

        public static Issue info(String code, String message) {
            return new Issue(code, message, Severity.INFO, null);
        }

        public static Issue info(String code, String message, String field) {
            return new Issue(code, message, Severity.INFO, field);
        }
    }

    /**
     * Severity levels for validation issues.
     */
    public enum Severity {
        ERROR, WARNING, INFO
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult okWithMetrics(String version, String driver, long latency) {
        return new ValidationResult(true, List.of(), version, driver, latency);
    }

    public static ValidationResult failed(String... errorMessages) {
        var issues = List.of(errorMessages).stream()
                .map(msg -> Issue.error("VALIDATION_ERROR", msg))
                .toList();
        return new ValidationResult(false, issues);
    }

    public static ValidationResult failed(List<String> errorMessages) {
        var issues = errorMessages.stream()
                .map(msg -> Issue.error("VALIDATION_ERROR", msg))
                .toList();
        return new ValidationResult(false, issues);
    }

    public static ValidationResult failedWithWarnings(List<String> errors, List<String> warnings) {
        var issues = errors.stream()
                .map(msg -> Issue.error("VALIDATION_ERROR", msg))
                .toList();
        if (warnings != null && !warnings.isEmpty()) {
            var warningIssues = warnings.stream()
                    .map(msg -> Issue.warning("VALIDATION_WARNING", msg))
                    .toList();
            issues = new java.util.ArrayList<>(issues);
            issues.addAll(warningIssues);
        }
        return new ValidationResult(false, issues);
    }

    /**
     * Builder for constructing validation results with multiple issues.
     */
    public static class Builder {

        private final List<Issue> issues = new java.util.ArrayList<>();
        private final boolean isOk;

        public Builder() {
            this.isOk = true;
        }

        private Builder(boolean isOk) {
            this.isOk = isOk;
        }

        public static Builder ok() {
            return new Builder();
        }

        public static Builder failed() {
            return new Builder(false);
        }

        public Builder error(String code, String message) {
            issues.add(Issue.error(code, message));
            return this;
        }

        public Builder error(String code, String message, String field) {
            issues.add(Issue.error(code, message, field));
            return this;
        }

        public Builder warning(String code, String message) {
            issues.add(Issue.warning(code, message));
            return this;
        }

        public Builder warning(String code, String message, String field) {
            issues.add(Issue.warning(code, message, field));
            return this;
        }

        public Builder info(String code, String message) {
            issues.add(Issue.info(code, message));
            return this;
        }

        public Builder info(String code, String message, String field) {
            issues.add(Issue.info(code, message, field));
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(isOk && issues.isEmpty(), List.copyOf(issues));
        }
    }
}
