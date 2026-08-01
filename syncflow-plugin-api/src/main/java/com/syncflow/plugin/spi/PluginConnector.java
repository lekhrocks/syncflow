package com.syncflow.plugin.spi;

import com.syncflow.plugin.capabilities.ConnectorCapabilities;
import com.syncflow.plugin.descriptor.PluginDescriptor;

import java.util.List;
import java.util.Map;

public interface PluginConnector {

    PluginDescriptor descriptor();

    ConnectorCapabilities capabilities();

    /** Health check; returns UP/DOWN/DEGRADED/UNKNOWN as a string. */
    String health();

    /** Human-readable metadata like version, vendor. */
    Map<String, String> metadata();

    List<String> discoverSchemas(PluginContext context);

    List<String> discoverTables(PluginContext context, String schema);

    List<Map<String, Object>> discoverColumns(PluginContext context, String schema, String table);
}
