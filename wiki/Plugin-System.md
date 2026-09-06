# Plugin System

SyncFlow supports third-party connectors via a plugin SDK with isolated ClassLoader loading.

## Architecture

```
syncflow-plugin-api (standalone JAR)
  ├── PluginConnector (SPI interface)
  ├── PluginDescriptor (manifest-driven metadata)
  ├── PluginLifecycle (install/enable/disable/uninstall)
  ├── ConfigurationSchema (settings schema)
  └── Capability interfaces (CdcProvider, SnapshotProvider, DestinationWriterProvider)

PluginManager (in syncflow-api)
  ├── URLClassLoader per plugin (isolation)
  ├── MANIFEST.MF scanning (Plugin-Connector-Class)
  └── Versioned compatibility checks
```

## Plugin Structure

A plugin is a JAR with:

```
my-connector-plugin.jar
├── META-INF/
│   └── MANIFEST.MF
│       Plugin-Connector-Class: com.example.MyConnector
│       Plugin-Version: 1.0.0
│       Plugin-Name: My Custom Connector
│       Plugin-Description: Connects to MyDatabase
│       Plugin-Platform-Version: 1
├── com/example/MyConnector.class
└── lib/
    └── my-database-driver.jar
```

## Plugin SPI

```java
public interface PluginConnector extends Connector {
    // Standard connector methods from core SPI
}

public interface CdcProvider {
    CdcCapableConnector createCdcConnector(Map<String, String> config);
}

public interface SnapshotProvider {
    SnapshotCapableConnector createSnapshotConnector(Map<String, String> config);
}

public interface DestinationWriterProvider {
    DestinationWriter createWriter(Map<String, String> config);
}
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/plugins` | List installed plugins |
| `GET` | `/api/plugins/{id}` | Get plugin details |
| `POST` | `/api/plugins/install` | Install plugin JAR |
| `POST` | `/api/plugins/{id}/enable` | Enable plugin |
| `POST` | `/api/plugins/{id}/disable` | Disable plugin |
| `DELETE` | `/api/plugins/{id}` | Uninstall plugin |
| `GET` | `/api/plugins/{id}/capabilities` | Get plugin capabilities |

## Isolation

Each plugin runs in its own `URLClassLoader`:

- Plugin classes are invisible to other plugins and the main application
- Plugin dependencies (in `lib/`) are loaded from the plugin JAR
- No classpath pollution
- Plugin can use different library versions than the host

## Versioned Compatibility

```java
public interface PluginDescriptor {
    String name();
    String version();
    int minimumPlatformVersion();  // Minimum SyncFlow version required
    int maximumPlatformVersion();  // Maximum SyncFlow version supported (0 = no limit)
}
```

The plugin manager rejects plugins that declare incompatible platform versions.

## Lifecycle

```
INSTALLED → ENABLED → DISABLED → UNINSTALLED
     ↑          ↓
     └──────────┘
```

- **INSTALLED** — JAR loaded, classes available
- **ENABLED** — Plugin active, can handle connections
- **DISABLED** — Plugin paused, existing connections continue
- **UNINSTALLED** — JAR removed, ClassLoader closed

## Writing a Plugin

1. Create a new Java project with `syncflow-plugin-api` as `compileOnly` dependency
2. Implement `PluginConnector` (or subset of SPI)
3. Implement capability providers (`CdcProvider`, `SnapshotProvider`, `DestinationWriterProvider`)
4. Create `META-INF/MANIFEST.MF` with `Plugin-Connector-Class`
5. Package as JAR with dependencies in `lib/`
6. Install via `POST /api/plugins/install` or drop in plugin directory

## Example

```java
public class MyDatabaseConnector implements PluginConnector {
    @Override
    public ConnectorType type() {
        return ConnectorType.GENERIC_JDBC;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(false, true, true, false, false);
        // CDC=no, Snapshot=yes, Metadata=yes, Transactions=no, Offsets=no
    }

    @Override
    public void connect(ConnectionConfiguration config) {
        // Establish connection
    }

    // ... other SPI methods
}
```
