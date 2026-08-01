package com.syncflow.core.connection;

import java.util.UUID;

public record ConnectionId(String value) {

    public ConnectionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ConnectionId must not be blank");
        }
    }

    public static ConnectionId generate() {
        return new ConnectionId(UUID.randomUUID().toString());
    }

    public static ConnectionId from(String value) {
        return new ConnectionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
