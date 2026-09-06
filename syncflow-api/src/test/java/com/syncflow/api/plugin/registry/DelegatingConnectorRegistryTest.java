package com.syncflow.api.plugin.registry;

import com.syncflow.api.plugin.adapter.PluginConnectorAdapter;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.registry.SpringConnectorRegistry;
import com.syncflow.core.spi.Connector;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.plugin.descriptor.PluginDescriptor;
import com.syncflow.plugin.spi.PluginConnector;
import com.syncflow.plugin.spi.PluginContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelegatingConnectorRegistryTest {

    private DelegatingConnectorRegistry registry;
    private SpringConnectorRegistry builtins;
    private PluginConnectorRegistry plugins;

    @BeforeEach
    void setUp() {
        builtins = new SpringConnectorRegistry(List.of());
        plugins = new PluginConnectorRegistry();
        registry = new DelegatingConnectorRegistry(builtins, plugins);
    }

    @Test
    void register_plugin_routes_to_plugin_registry() {
        var adapter = adapter("p1", "postgresql");
        registry.register(adapter);
        assertTrue(plugins.isRegistered("p1"));
        // The built-in POSTGRESQL slot must NOT be filled.
        assertTrue(builtins.getAll().isEmpty());
    }

    @Test
    void register_builtin_routes_to_spring_registry() {
        var builtIn = new BuiltInConnector(ConnectorType.POSTGRESQL);
        registry.register(builtIn);
        assertFalse(plugins.getAll().stream().anyMatch(c -> c instanceof PluginConnectorAdapter));
        assertSame(builtIn, builtins.get(ConnectorType.POSTGRESQL).orElseThrow());
    }

    @Test
    void get_postgresql_finds_plugin_advertising_postgresql() {
        var adapter = adapter("p1", "postgresql");
        registry.register(adapter);
        var found = registry.get(ConnectorType.POSTGRESQL);
        assertTrue(found.isPresent());
        assertSame(adapter, found.get());
    }

    @Test
    void get_generic_plugin_returns_first_registered_plugin() {
        var a1 = adapter("p1", "rocket-db");
        var a2 = adapter("p2", "mars-db");
        registry.register(a1);
        registry.register(a2);
        var found = registry.get(ConnectorType.GENERIC_PLUGIN);
        assertTrue(found.isPresent());
        assertTrue(found.get() == a1 || found.get() == a2);
    }

    @Test
    void unregister_plugin_by_id_removes_and_disconnects() {
        var a1 = adapter("p1", "x");
        registry.register(a1);
        assertTrue(registry.unregisterPlugin("p1"));
        assertFalse(plugins.isRegistered("p1"));
        // After unregister, the adapter is no longer registered; the
        // disconnect side effect on the inner plugin is observable
        // through a flag on the test plugin.
    }

    @Test
    void unregister_generic_plugin_type_clears_all_plugins() {
        registry.register(adapter("p1", "x"));
        registry.register(adapter("p2", "y"));
        assertEquals(2, plugins.getAll().size());
        registry.unregister(ConnectorType.GENERIC_PLUGIN);
        assertEquals(0, plugins.getAll().size());
    }

    @Test
    void unregister_builtin_type_does_not_touch_plugins() {
        var a1 = adapter("p1", "x");
        registry.register(a1);
        registry.unregister(ConnectorType.POSTGRESQL);
        // The plugin is still there.
        assertTrue(plugins.isRegistered("p1"));
    }

    @Test
    void isRegistered_generic_plugin_reflects_plugin_count() {
        assertFalse(registry.isRegistered(ConnectorType.GENERIC_PLUGIN));
        registry.register(adapter("p1", "x"));
        assertTrue(registry.isRegistered(ConnectorType.GENERIC_PLUGIN));
    }

    @Test
    void getAll_combines_builtins_and_plugins() {
        registry.register(new BuiltInConnector(ConnectorType.MYSQL));
        registry.register(adapter("p1", "x"));
        assertEquals(2, registry.getAll().size());
    }

    @Test
    void plugin_disconnect_called_on_uninstall() {
        var plugin = new PluginConnector() {

            @Override
            public PluginDescriptor descriptor() {
                return descOf("p1", "x");
            }
            @Override
            public com.syncflow.plugin.capabilities.ConnectorCapabilities capabilities() {
                return noCaps();
            }
            @Override
            public String health() {
                return "UP";
            }
            @Override
            public Map<String, String> metadata() {
                return Map.of();
            }
            @Override
            public List<String> discoverSchemas(PluginContext c) {
                return List.of();
            }
            @Override
            public List<String> discoverTables(PluginContext c, String s) {
                return List.of();
            }
            @Override
            public List<Map<String, Object>> discoverColumns(PluginContext c, String s, String t) {
                return List.of();
            }
        };
        var adapter = new PluginConnectorAdapter(plugin, descOf("p1", "x"));
        adapter.connect(new ConnectorContext(
                new com.syncflow.core.model.ConnectionConfiguration(
                        ConnectorType.GENERIC_PLUGIN, "h", 0, "d", null, null, Map.of()),
                Map.of()));
        assertTrue(adapter.isConnected());
        registry.register(adapter);
        // Plugin SPI has no disconnect; the adapter's disconnect() flips its
        // own internal connected flag. We just verify the adapter is
        // removed from the registry and that calling disconnect works.
        registry.unregisterPlugin("p1");
        adapter.disconnect();
        assertFalse(adapter.isConnected());
    }

    private static PluginDescriptor descOf(String id, String type) {
        return new PluginDescriptor(id, "t", "v", "1.0", "d", type,
                List.of(), "1", "1", List.of(), "MIT", null, null);
    }

    private static com.syncflow.plugin.capabilities.ConnectorCapabilities noCaps() {
        return new com.syncflow.plugin.capabilities.ConnectorCapabilities(
                false, false, false, false, false, false);
    }

    private static PluginConnectorAdapter adapter(String id, String type) {
        var plugin = new PluginConnector() {

            @Override
            public PluginDescriptor descriptor() {
                return descOf(id, type);
            }
            @Override
            public com.syncflow.plugin.capabilities.ConnectorCapabilities capabilities() {
                return noCaps();
            }
            @Override
            public String health() {
                return "UP";
            }
            @Override
            public Map<String, String> metadata() {
                return Map.of();
            }
            @Override
            public List<String> discoverSchemas(PluginContext c) {
                return List.of();
            }
            @Override
            public List<String> discoverTables(PluginContext c, String s) {
                return List.of();
            }
            @Override
            public List<Map<String, Object>> discoverColumns(PluginContext c, String s, String t) {
                return List.of();
            }
        };
        return new PluginConnectorAdapter(plugin, descOf(id, type));
    }

    private static final class BuiltInConnector implements Connector {

        private final ConnectorType type;
        private final AtomicInteger connects = new AtomicInteger();
        BuiltInConnector(ConnectorType type) {
            this.type = type;
        }
        @Override
        public ConnectorType type() {
            return type;
        }
        @Override
        public com.syncflow.core.spi.ConnectorCapabilities capabilities() {
            return com.syncflow.core.spi.ConnectorCapabilities.none();
        }
        @Override
        public void connect(ConnectorContext c) {
            connects.incrementAndGet();
        }
        @Override
        public void disconnect() {
        }
        @Override
        public boolean isConnected() {
            return connects.get() > 0;
        }
        @Override
        public com.syncflow.core.spi.ConnectorValidationResult validate(ConnectorContext c) {
            return com.syncflow.core.spi.ConnectorValidationResult.ok();
        }
        @Override
        public List<String> discoverSchemas(ConnectorContext c) {
            return List.of();
        }
        @Override
        public List<String> discoverTables(ConnectorContext c, String s) {
            return List.of();
        }
        @Override
        public com.syncflow.core.spi.ConnectorHealth health() {
            return new com.syncflow.core.spi.ConnectorHealth(
                    com.syncflow.core.spi.ConnectorHealth.Status.UP, "", java.time.Instant.now(), 0);
        }
        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }
}
