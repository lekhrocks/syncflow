package com.syncflow.api.connection.dto;

public record TestConnectionResponse(
        boolean success,
        long latencyMs,
        String databaseVersion,
        String driverName,
        String errorMessage) {
}
