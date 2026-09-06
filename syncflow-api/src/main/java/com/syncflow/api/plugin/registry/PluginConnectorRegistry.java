package com.syncflow.api.plugin.registry;

import com.syncflow.core.spi.Connector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the currently-enabled plugin connectors, keyed by plugin id.
 *
 * <p>
 * This is not a {@code ConnectorRegistry} itself — it is a sidecar
 * store consulted by {@link DelegatingConnectorRegistry} for
 * plugin-backed lookups. Each entry is one
 * {@link com.syncflow.api.plugin.adapter.PluginConnectorAdapter}
 * (which implements every core SPI sub-interface).
 */
@Component
public class PluginConnectorRegistry {

    private final Map<String, Connector> byPluginId = new ConcurrentHashMap<>();

    public Connector register(String pluginId, Connector adapter) {
        byPluginId.put(pluginId, adapter);
        return adapter;
    }

    public Optional<Connector> get(String pluginId) {
        return Optional.ofNullable(byPluginId.get(pluginId));
    }

    public boolean unregister(String pluginId) {
        return byPluginId.remove(pluginId) != null;
    }

    public List<Connector> getAll() {
        return List.copyOf(byPluginId.values());
    }

    public boolean isRegistered(String pluginId) {
        return byPluginId.containsKey(pluginId);
    }
}
