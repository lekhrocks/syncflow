package com.syncflow.core.pipeline.validation;

import java.util.List;

/**
 * Pipeline-design validation result.
 *
 * <p>
 * Unified with
 * {@link com.syncflow.common.validation.ValidationResult} in
 * {@code syncflow-common}. This class now delegates to the common type; the
 * {@link ValidationIssue} type is preserved so existing
 * {@link PipelineValidator}
 * call sites compile unchanged.
 *
 * <p>
 * Use {@link #toCommon()} to obtain a
 * {@link com.syncflow.common.validation.ValidationResult} for cross-module
 * consumers.
 */
public record ValidationResult(
        boolean valid,
        List<ValidationIssue> issues) {

    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(List<ValidationIssue> issues) {
        return new ValidationResult(false, List.copyOf(issues));
    }

    /**
     * Convert to the unified
     * {@link com.syncflow.common.validation.ValidationResult}
     * in {@code syncflow-common}. Each {@link ValidationIssue} is mapped to a
     * {@link com.syncflow.common.validation.ValidationResult.Issue} preserving
     * code, field, message, and severity.
     */
    public com.syncflow.common.validation.ValidationResult toCommon() {
        var builder = com.syncflow.common.validation.ValidationResult.Builder.ok();
        for (var issue : issues) {
            if (issue.severity() == ValidationIssue.Severity.ERROR) {
                builder.error(issue.code(), issue.message(), issue.field());
            } else if (issue.severity() == ValidationIssue.Severity.WARNING) {
                builder.warning(issue.code(), issue.message(), issue.field());
            } else {
                builder.info(issue.code(), issue.message(), issue.field());
            }
        }
        return builder.build();
    }
}
