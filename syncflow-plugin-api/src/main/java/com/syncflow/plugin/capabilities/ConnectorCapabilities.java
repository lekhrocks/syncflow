package com.syncflow.plugin.capabilities;

public record ConnectorCapabilities(
        boolean supportsMetadata,
        boolean supportsSnapshot,
        boolean supportsCdc,
        boolean supportsDestination,
        boolean supportsTransactions,
        boolean supportsStreaming) {
}
