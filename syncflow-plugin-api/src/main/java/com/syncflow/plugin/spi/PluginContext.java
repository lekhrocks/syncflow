package com.syncflow.plugin.spi;

import java.util.Map;

public record PluginContext(
        String host,
        int port,
        String database,
        String username,
        String password,
        Map<String, String> properties) {

    public PluginContext {
        properties = Map.copyOf(properties == null ? Map.of() : properties);
    }
}
