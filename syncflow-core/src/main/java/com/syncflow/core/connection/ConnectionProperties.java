package com.syncflow.core.connection;

import java.util.Map;

public record ConnectionProperties(
        ConnectionType type,
        String host,
        int port,
        String database,
        Map<String, String> options) {

    public ConnectionProperties {
        if (type == null)
            throw new IllegalArgumentException("type must not be null");
        if (host == null || host.isBlank())
            throw new IllegalArgumentException("host must not be blank");
        if (port <= 0 || port > 65535)
            throw new IllegalArgumentException("port must be between 1 and 65535");
        if (database == null || database.isBlank())
            throw new IllegalArgumentException("database must not be blank");
        options = Map.copyOf(options == null ? Map.of() : options);
    }

    public ConnectionProperties withType(ConnectionType type) {
        return new ConnectionProperties(type, host, port, database, options);
    }
}
