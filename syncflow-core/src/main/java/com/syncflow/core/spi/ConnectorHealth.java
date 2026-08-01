package com.syncflow.core.spi;

import java.time.Instant;

public record ConnectorHealth(
        Status status,
        String message,
        Instant lastChecked,
        long latencyMs) {

    public enum Status {
        UP, DOWN, DEGRADED, UNKNOWN
    }

    public static ConnectorHealth up(long latencyMs) {
        return new ConnectorHealth(Status.UP, "Connected", Instant.now(), latencyMs);
    }

    public static ConnectorHealth down(String message) {
        return new ConnectorHealth(Status.DOWN, message, Instant.now(), 0);
    }

    public static ConnectorHealth unknown() {
        return new ConnectorHealth(Status.UNKNOWN, "Not checked", Instant.now(), 0);
    }
}
