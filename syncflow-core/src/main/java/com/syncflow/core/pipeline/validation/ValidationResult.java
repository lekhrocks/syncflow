package com.syncflow.core.pipeline.validation;

import java.util.List;

/**
 * Pipeline-design validation result: carries structured issues with severity
 * grades for the pipeline designer UI. Distinct from
 * {@link com.syncflow.core.spi.ConnectorValidationResult}, which is a
 * simpler connector-SPI response.
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
}
