package com.syncflow.api.plugin.registry;

import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.registry.SpringConnectorRegistry;
import com.syncflow.core.spi.Connector;
import com.syncflow.api.plugin.adapter.PluginConnectorAdapter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single {@code ConnectorRegistry} seen by the rest of the app.
 * Routes built-in connectors to the existing
 * {@link SpringConnectorRegistry} and plugin-backed connectors to the
 * sidecar {@link PluginConnectorRegistry}.
 *
 * <p>
 * Plugin connectors carry a {@code pluginId} in the
 * {@link PluginConnectorAdapter}. The
 * {@link ConnectorType#GENERIC_PLUGIN} value is the
 * "look up any plugin" sentinel — used by health checks and dashboards
 * that don't care which plugin a connection is bound to.
 */
@Component
@Primary
public class DelegatingConnectorRegistry implements ConnectorRegistry {

    private final SpringConnectorRegistry builtins;
    private final PluginConnectorRegistry plugins;

    public DelegatingConnectorRegistry(SpringConnectorRegistry builtins,
            PluginConnectorRegistry plugins) {
        this.builtins = builtins;
        this.plugins = plugins;
    }

    @Override
    public Connector register(Connector connector) {
        if (connector instanceof PluginConnectorAdapter adapter) {
            plugins.register(adapter.pluginId(), adapter);
        } else {
            builtins.register(connector);
        }
        return connector;
    }

    @Override
    public void unregister(ConnectorType type) {
        // Unregister by type disambiguates: GENERIC_PLUGIN removes all
        // plugins; any other type only hits the built-in registry.
        if (type == ConnectorType.GENERIC_PLUGIN) {
            for (var c : plugins.getAll()) {
                c.disconnect();
            }
            plugins.getAll().forEach(c -> plugins.unregister(pluginIdOf(c)));
        } else {
            builtins.unregister(type);
        }
    }

    @Override
    public Optional<Connector> get(ConnectorType type) {
        if (type == ConnectorType.GENERIC_PLUGIN) {
            var all = plugins.getAll();
            return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
        }
        // Look in the built-in registry first; if a plugin advertises the
        // same type (e.g. a "postgresql" plugin), it can shadow the
        // built-in for that type. This lets users override built-ins
        // without code changes to the core.
        var builtin = builtins.get(type);
        if (builtin.isPresent()) {
            for (var c : plugins.getAll()) {
                if (c.type() == type) {
                    return Optional.of(c);
                }
            }
            return builtin;
        }
        // No built-in for this type — fall back to a matching plugin.
        for (var c : plugins.getAll()) {
            if (c.type() == type) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Connector> getAll() {
        var all = new ArrayList<Connector>();
        all.addAll(builtins.getAll());
        all.addAll(plugins.getAll());
        return all;
    }

    @Override
    public boolean isRegistered(ConnectorType type) {
        if (type == ConnectorType.GENERIC_PLUGIN) {
            return !plugins.getAll().isEmpty();
        }
        return builtins.isRegistered(type);
    }

    /**
     * Unregister a single plugin by id — used by PluginManager on
     * disable/uninstall. Disconnects the adapter first to release
     * resources (DB handles, CDC threads) before removing from the map.
     */
    public boolean unregisterPlugin(String pluginId) {
        var connector = plugins.get(pluginId);
        connector.ifPresent(Connector::disconnect);
        return plugins.unregister(pluginId);
    }

    private static String pluginIdOf(Connector c) {
        return c instanceof PluginConnectorAdapter pa ? pa.pluginId() : null;
    }
}
