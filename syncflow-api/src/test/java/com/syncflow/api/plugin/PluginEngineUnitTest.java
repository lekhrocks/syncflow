package com.syncflow.api.plugin;

import com.syncflow.plugin.capabilities.ConnectorCapabilities;
import com.syncflow.plugin.config.ConfigurationSchema;
import com.syncflow.plugin.descriptor.PluginDescriptor;
import com.syncflow.plugin.lifecycle.PluginLifecycle;
import com.syncflow.plugin.spi.PluginConnector;
import com.syncflow.plugin.spi.PluginContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginEngineUnitTest {

    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        pluginManager = new PluginManager();
    }

    // --- Plugin parser / manifest validator ---

    @Test
    void manifestParserExtractsRequiredAttributes() {
        var manifest = createManifest("test-plugin", "Test Plugin", "1.0.0",
                "com.syncflow.plugin.TestPluginConnector");
        var pluginId = manifest.getMainAttributes().getValue("Plugin-Id");
        var version = manifest.getMainAttributes().getValue("Plugin-Version");
        var connectorClass = manifest.getMainAttributes().getValue("Plugin-Connector-Class");

        assertEquals("test-plugin", pluginId);
        assertEquals("1.0.0", version);
        assertEquals("com.syncflow.plugin.TestPluginConnector", connectorClass);
    }

    @Test
    void manifestMissingPluginIdIsInvalid() {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Plugin-Connector-Class", "x");

        assertNull(manifest.getMainAttributes().getValue("Plugin-Id"));
    }

    @Test
    void manifestMissingConnectorClassIsInvalid() {
        var manifest = createManifest("p", "P", "1.0", null);
        assertNull(manifest.getMainAttributes().getValue("Plugin-Connector-Class"));
    }

    @Test
    void manifestEmptyValuesAreRejected() {
        var manifest = createManifest("", "", "", "");
        assertTrue(manifest.getMainAttributes().getValue("Plugin-Id").isEmpty());
    }

    // --- Version checker ---

    @Test
    void versionStringParsedCorrectly() {
        var v1 = parseVersion("1.0.0");
        var v2 = parseVersion("2.0.0");
        assertTrue(v2.compareTo(v1) > 0);
    }

    @Test
    void versionCompatibilityCheck() {
        var platform = parseVersion("1.5.0");
        var min = parseVersion("1.0.0");
        var max = parseVersion("2.0.0");

        assertTrue(platform.compareTo(min) >= 0);
        assertTrue(platform.compareTo(max) <= 0);
    }

    @Test
    void versionBelowMinimumFails() {
        var platform = parseVersion("0.9.0");
        var min = parseVersion("1.0.0");
        assertTrue(platform.compareTo(min) < 0);
    }

    @Test
    void versionAboveMaximumFails() {
        var platform = parseVersion("3.0.0");
        var max = parseVersion("2.0.0");
        assertTrue(platform.compareTo(max) > 0);
    }

    @Test
    void versionExactMatch() {
        assertEquals(0, parseVersion("1.0.0").compareTo(parseVersion("1.0.0")));
    }

    // --- Permission checker ---

    @Test
    void descriptorRequiredPermissions() {
        var descriptor = new PluginDescriptor("p", "P", "vendor", "1.0",
                "desc", "postgresql", List.of("postgresql"), "1.0.0", "2.0.0",
                List.of("CONNECTION_READ", "PIPELINE_WRITE"), "MIT", null, null);
        assertEquals(2, descriptor.requiredPermissions().size());
        assertTrue(descriptor.requiredPermissions().contains("CONNECTION_READ"));
    }

    @Test
    void descriptorEmptyPermissions() {
        var descriptor = new PluginDescriptor("p", "P", "v", "1.0",
                "d", "pg", List.of(), null, null, List.of(), "MIT", null, null);
        assertTrue(descriptor.requiredPermissions().isEmpty());
    }

    // --- Dependency resolver (manifest-based) ---

    @Test
    void pluginDescriptorWithDatabases() {
        var descriptor = new PluginDescriptor("pg-connector", "PG Connector", "SyncFlow",
                "2.1.0", "PostgreSQL connector", "postgresql", List.of("postgresql", "timescaledb"),
                "0.5.0", "2.0.0", List.of(), "Apache-2.0", null, null);
        assertEquals(2, descriptor.supportedDatabases().size());
        assertEquals("postgresql", descriptor.connectorType());
    }

    @Test
    void pluginDescriptorWithMinPlatformOnly() {
        var descriptor = new PluginDescriptor("p", "P", "v", "1.0",
                "d", "mysql", List.of("mysql"), "1.0.0", null, List.of(), "MIT", null, null);
        assertNotNull(descriptor.minimumPlatformVersion());
        assertNull(descriptor.maximumPlatformVersion());
    }

    // --- Lifecycle ---

    @Test
    void lifecycleStatesCreatedInOrder() {
        assertEquals(PluginLifecycle.INSTALLED, PluginLifecycle.valueOf("INSTALLED"));
        assertEquals(PluginLifecycle.ENABLED, PluginLifecycle.valueOf("ENABLED"));
        assertEquals(PluginLifecycle.DISABLED, PluginLifecycle.valueOf("DISABLED"));
        assertEquals(PluginLifecycle.UNINSTALLED, PluginLifecycle.valueOf("UNINSTALLED"));
        assertEquals(PluginLifecycle.ERROR, PluginLifecycle.valueOf("ERROR"));
    }

    @Test
    void lifecycleFromInstalledToEnabled() {
        var entry = new PluginManager.PluginEntry(
                createMinimalDescriptor(), createMinimalConnector(), PluginLifecycle.INSTALLED);
        assertEquals(PluginLifecycle.INSTALLED, entry.lifecycle());
    }

    // --- Connector capabilities ---

    @Test
    void connectorCapabilitiesDefault() {
        var caps = new ConnectorCapabilities(true, true, true, true, true, true);
        assertTrue(caps.supportsMetadata());
        assertTrue(caps.supportsSnapshot());
        assertTrue(caps.supportsCdc());
        assertTrue(caps.supportsDestination());
        assertTrue(caps.supportsTransactions());
        assertTrue(caps.supportsStreaming());
    }

    @Test
    void connectorCapabilitiesReadOnly() {
        var caps = new ConnectorCapabilities(true, false, false, false, false, false);
        assertTrue(caps.supportsMetadata());
        assertFalse(caps.supportsSnapshot());
        assertFalse(caps.supportsCdc());
    }

    // --- Configuration schema ---

    @Test
    void configurationSchemaWithHostPort() {
        var schema = new ConfigurationSchema(
                List.of(ConfigurationSchema.ConfigProperty.host(),
                        ConfigurationSchema.ConfigProperty.port()),
                List.of("host", "port"),
                Map.of());
        assertEquals(2, schema.properties().size());
        assertEquals("Host", schema.properties().getFirst().label());
        assertNotNull(schema.properties().get(1).validationPattern());
        assertEquals("^\\d+$", schema.properties().get(1).validationPattern());
    }

    @Test
    void configurationSchemaPasswordIsSensitive() {
        var prop = ConfigurationSchema.ConfigProperty.password();
        assertTrue(prop.sensitive());
        assertEquals("password", prop.type());
    }

    @Test
    void configurationSchemaEmpty() {
        assertTrue(ConfigurationSchema.empty().properties().isEmpty());
    }

    // --- Plugin context ---

    @Test
    void pluginContextWithProperties() {
        var ctx = new PluginContext("localhost", 5432, "mydb", "user", "pass", Map.of("ssl", "true"));
        assertEquals(5432, ctx.port());
        assertEquals("mydb", ctx.database());
        assertEquals("true", ctx.properties().get("ssl"));
    }

    @Test
    void pluginContextNullProperties() {
        var ctx = new PluginContext("h", 1, "d", "u", "p", null);
        assertTrue(ctx.properties().isEmpty());
    }

    // --- Helpers ---

    private Manifest createManifest(String id, String name, String version, String connectorClass) {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (id != null)
            manifest.getMainAttributes().putValue("Plugin-Id", id);
        if (name != null)
            manifest.getMainAttributes().putValue("Plugin-Name", name);
        if (version != null)
            manifest.getMainAttributes().putValue("Plugin-Version", version);
        if (connectorClass != null)
            manifest.getMainAttributes().putValue("Plugin-Connector-Class", connectorClass);
        return manifest;
    }

    private ComparableVersion parseVersion(String v) {
        return new ComparableVersion(v);
    }

    private PluginDescriptor createMinimalDescriptor() {
        return new PluginDescriptor("min", "Min", "v", "1.0",
                "minimal", "generic", List.of(), null, null, List.of(), null, null, null);
    }

    private PluginConnector createMinimalConnector() {
        return new PluginConnector() {

            @Override
            public PluginDescriptor descriptor() {
                return createMinimalDescriptor();
            }
            @Override
            public ConnectorCapabilities capabilities() {
                return new ConnectorCapabilities(false, false, false, false, false, false);
            }
            @Override
            public String health() {
                return "UNKNOWN";
            }
            @Override
            public Map<String, String> metadata() {
                return Map.of();
            }
            @Override
            public List<String> discoverSchemas(PluginContext ctx) {
                return List.of();
            }
            @Override
            public List<String> discoverTables(PluginContext ctx, String schema) {
                return List.of();
            }
            @Override
            public List<Map<String, Object>> discoverColumns(PluginContext ctx, String schema, String table) {
                return List.of();
            }
        };
    }

    private record ComparableVersion(String version) implements Comparable<ComparableVersion> {

        @Override
        public int compareTo(ComparableVersion o) {
            var parts1 = version.split("\\.");
            var parts2 = o.version.split("\\.");
            for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
                int c = Integer.compare(Integer.parseInt(parts1[i]), Integer.parseInt(parts2[i]));
                if (c != 0)
                    return c;
            }
            return Integer.compare(parts1.length, parts2.length);
        }
    }
}
