package com.syncflow.core.pipeline.validation;

public record ValidationIssue(
        String code,
        String field,
        String message,
        Severity severity) {

    public enum Severity {
        ERROR, WARNING, INFO
    }
}
