package com.syncflow.core.cdc;

public record EventSource(
        String database,
        String schema,
        String table,
        String connectorType) {
}
