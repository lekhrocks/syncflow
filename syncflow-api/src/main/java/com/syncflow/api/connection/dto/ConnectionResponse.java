package com.syncflow.api.connection.dto;

import com.syncflow.core.connection.Connection;

import java.time.Instant;
import java.util.Map;

public record ConnectionResponse(
        String id,
        String name,
        String connectionType,
        String host,
        int port,
        String database,
        Map<String, String> options,
        String status,
        String databaseVersion,
        String driverName,
        long lastLatencyMs,
        Instant lastChecked,
        Instant createdAt,
        Instant updatedAt) {

    public static ConnectionResponse from(Connection c, boolean maskPassword) {
        return new ConnectionResponse(
                c.getId().value(), c.getName(),
                c.getProperties().type().name(),
                c.getProperties().host(), c.getProperties().port(),
                c.getProperties().database(), c.getProperties().options(),
                c.getStatus().name(),
                c.getMetadata().databaseVersion(),
                c.getMetadata().driverName(),
                c.getMetadata().latencyMs(),
                c.getMetadata().lastChecked(),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    // ponytail: never expose credentials in API responses.
}
