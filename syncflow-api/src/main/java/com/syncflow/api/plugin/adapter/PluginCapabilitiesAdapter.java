package com.syncflow.api.plugin.adapter;

import com.syncflow.core.spi.ConnectorCapabilities;

/**
 * Maps the 6-boolean plugin capabilities to the 5-boolean core capabilities.
 *
 * <p>
 * Plugin SPI: metadata, snapshot, cdc, destination, transactions, streaming.
 * <br>
 * Core SPI: cdc, snapshot, schemaDiscovery, transactions, offsetTracking.
 */
public final class PluginCapabilitiesAdapter {

    private PluginCapabilitiesAdapter() {
    }

    public static ConnectorCapabilities map(
            com.syncflow.plugin.capabilities.ConnectorCapabilities plugin) {
        if (plugin == null) {
            return ConnectorCapabilities.none();
        }
        return new ConnectorCapabilities(
                plugin.supportsCdc(),
                plugin.supportsSnapshot(),
                plugin.supportsMetadata(), // → schemaDiscovery
                plugin.supportsTransactions(),
                // No direct mapping: streaming/cdc imply offset tracking.
                plugin.supportsCdc() || plugin.supportsStreaming());
    }
}
