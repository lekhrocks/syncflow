# Connectors

SyncFlow uses a SPI (Service Provider Interface) architecture for connectors. Each connector implements one or more capability interfaces.

## Connector SPI Hierarchy

```
Connector (base)
  ├── type(), capabilities(), connect(), disconnect(), validate()
  ├── discoverSchemas(), discoverTables(), health(), metadata()
  │
  ├── MetadataCapableConnector
  │     └── discoverColumns(), discoverConstraints(), discoverIndexes()
  │
  ├── SnapshotCapableConnector
  │     └── readBatch(), estimateRows(), rangeChunks(), snapshotClone(), streamRows()
  │
  └── CdcCapableConnector
        └── startCDC(), stopCDC(), pauseCDC(), resumeCDC(), captureStatus(), currentOffset()
```

## Supported Connectors

| Connector | Type | Capabilities | Implementation |
|-----------|------|-------------|----------------|
| PostgreSQL | `POSTGRESQL` | CDC, Snapshot, Metadata | `PostgresCdcConnector`, `PostgresMetadataConnector` |
| MySQL | `MYSQL` | CDC, Snapshot, Metadata | `MySqlCdcConnector`, `MySqlMetadataConnector` |
| MongoDB | `MONGODB` | CDC, Snapshot, Metadata | `MongoDbCdcConnector`, `MongoDbMetadataConnector` |
| Redis | `REDIS` | Metadata | `RedisMetadataConnector` |
| Kafka | `KAFKA` | CDC | `KafkaConnector` |

## Writers

| Writer | Purpose |
|--------|---------|
| `JdbcBatchWriter` | JDBC batch inserts/updates/deletes |
| `PooledJdbcBatchWriter` | HikariCP-pooled version (production) |
| `PostgresWriter` | PostgreSQL-specific with UPSERT |
| `MySqlWriter` | MySQL-specific with UPSERT |

## Connector Capabilities

```java
public record ConnectorCapabilities(
    boolean supportsCdc,
    boolean supportsSnapshot,
    boolean supportsSchemaDiscovery,
    boolean supportsTransactions,
    boolean supportsOffsetTracking
) {}
```

## Connection Types

Defined in `ConnectorType` enum:

```
POSTGRESQL, MYSQL, MONGODB, KAFKA, SQLSERVER, ORACLE,
ELASTICSEARCH, REDIS, GENERIC_JDBC
```

Not all types have implementations yet. The SPI allows adding new connectors without modifying core code.

## Writing a Custom Connector

See [[Plugin-System]] for third-party connector development. For internal connectors:

1. Implement `CdcCapableConnector` (or subset)
2. Add to `syncflow-connectors` module
3. Register via `@Component` (auto-discovered by Spring)
4. Add validator if needed (implements `ConnectorValidator`)

## Metadata Discovery

`MetadataCapableConnector` provides schema introspection:

```
GET /api/connections/{id}/metadata              → schemas
GET /api/connections/{id}/schemas/{schema}/tables → tables
GET /api/connections/{id}/schemas/{schema}/tables/{table}/columns → columns
GET /api/connections/{id}/schemas/{schema}/tables/{table}/indexes → indexes
GET /api/connections/{id}/schemas/{schema}/tables/{table}/constraints → constraints
```

Results are cached with configurable TTL (`syncflow.metadata.cache-ttl`, default 5m).
