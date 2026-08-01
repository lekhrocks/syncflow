package com.syncflow.api.connection.dto;

import java.time.Instant;

public record ConnectionHealthResponse(
        String status,
        long latencyMs,
        String databaseVersion,
        Instant lastChecked) {
}
