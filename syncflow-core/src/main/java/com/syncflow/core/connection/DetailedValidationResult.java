package com.syncflow.core.connection;

import java.util.List;

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
}
