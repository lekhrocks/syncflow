package com.syncflow.core.pipeline.validation;

import java.util.List;

public record ValidationResult(
        boolean valid,
        List<ValidationIssue> issues) {

    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(List<ValidationIssue> issues) {
        return new ValidationResult(false, List.copyOf(issues));
    }
}
