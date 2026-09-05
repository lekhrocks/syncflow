package com.syncflow.core.pipeline.validation;

import java.util.List;

import com.syncflow.common.validation.ValidationResult;

/**
 * Pipeline-design validation result.
 *
 * <p>
 * Unified with {@link com.syncflow.common.validation.ValidationResult} in
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
    public ValidationResult toCommon() {
        var commonIssues = issues.stream()
                .map(i -> new ValidationResult.Issue(
                        i.code(),
                        i.message(),
                        ValidationResult.Severity
                                .valueOf(i.severity().name()),
                        i.field()))
                .toList();
        return new ValidationResult(valid, commonIssues);
    }
}
