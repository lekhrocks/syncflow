package com.syncflow.core.spi;

import java.util.List;

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
