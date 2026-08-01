package com.syncflow.core.connection;

import java.time.Instant;

public record ConnectionMetadata(
        String databaseVersion,
        String driverName,
        long latencyMs,
        Instant lastChecked) {

    public static ConnectionMetadata unknown() {
        return new ConnectionMetadata("unknown", "unknown", 0, null);
    }
}
