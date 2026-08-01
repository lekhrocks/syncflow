package com.syncflow.core.spi;

public record ConnectorCapabilities(
        boolean supportsCdc,
        boolean supportsSnapshot,
        boolean supportsSchemaDiscovery,
        boolean supportsTransactions,
        boolean supportsOffsetTracking) {

    public static ConnectorCapabilities none() {
        return new ConnectorCapabilities(false, false, false, false, false);
    }

    public static ConnectorCapabilities full() {
        return new ConnectorCapabilities(true, true, true, true, true);
    }
}
