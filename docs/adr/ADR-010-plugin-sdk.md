# ADR-010: Why Plugin SDK?

## Status: Accepted

## Context
SyncFlow needs to support databases beyond PostgreSQL, MySQL, MongoDB, and Redis. Maintaining a connector per database from the SyncFlow team does not scale. External teams need the ability to build connectors without modifying the SyncFlow core.

## Decision
Extract the SPI (Service Provider Interface) into a standalone `syncflow-plugin-api` module published to Maven. Third-party connectors are packaged as JAR files and installed via the `PluginManager`.

## Rationale
- **Zero core dependencies**: The SDK is a JAR with no dependencies — no Spring, Jackson, or SyncFlow internals. Connectors compile independently of the platform.
- **Isolated ClassLoader**: Each plugin is loaded via its own `URLClassLoader`. If plugin A needs Jackson 2.x while the platform uses 3.x, both work correctly.
- **Manifest-driven loading**: Plugin JARs declare `Plugin-Connector-Class` in `MANIFEST.MF` — no module descriptors or SPI config files needed.
- **Versioned compatibility**: SDK versions are independent of platform versions. `PluginDescriptor.minimumPlatformVersion` / `maximumPlatformVersion` enforce compatibility at install time.

## Consequences
- Built-in connectors (Postgres, MySQL, MongoDB, Redis) use the same SPI as plugin connectors but are loaded as `@Component` beans — no installation step required.
- All plugin operations (install, enable, disable, uninstall) are logged to the audit store.
- 22 unit tests cover plugin manifest parsing, version checking, lifecycle states, and capability mapping.

## Links
- `syncflow-plugin-api/` module, `PluginManager.java`, `PluginController.java`
- `PluginEngineUnitTest.java` — 22 tests
- Platform version fields make version validation part of plugin installation
