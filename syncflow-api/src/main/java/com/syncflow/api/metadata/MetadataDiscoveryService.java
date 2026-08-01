package com.syncflow.api.metadata;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.MetadataResponse;
import com.syncflow.core.metadata.SchemaMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.MetadataCapableConnector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class MetadataDiscoveryService {

    private final ConnectionService connectionService;
    private final ConnectorRegistry connectorRegistry;
    private final MetadataCache cache;
    private final MeterRegistry meterRegistry;

    public MetadataDiscoveryService(ConnectionService connectionService,
            ConnectorRegistry connectorRegistry,
            MetadataCache cache,
            MeterRegistry meterRegistry) {
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
    }

    public MetadataResponse<SchemaMetadata> discoverSchemas(String connectionId) {
        var cached = cache.getSchemas(connectionId);
        return cached.map(schemaMetadata -> MetadataResponse.of(connectionId, "schemas", schemaMetadata, 0, true))
                .orElseGet(() -> execute(connectionId, "schemas", connector -> {
                    var schemas = connector.discoverSchemas(context(connectionId)).stream()
                            .map(name -> new SchemaMetadata(name, List.of()))
                            .toList();
                    cache.putSchemas(connectionId, schemas);
                    return schemas;
                }));
    }

    @SuppressWarnings("unchecked")
    private <T> MetadataResponse<T> execute(String connectionId, String type,
            Function<MetadataCapableConnector, List<?>> op) {
        var timer = Timer.builder("syncflow.metadata.discovery.duration")
                .tag("type", type)
                .register(meterRegistry);
        var sample = Timer.start(meterRegistry);
        try {
            var connector = resolveConnector(connectionId);
            var result = (List<T>) op.apply(connector);
            var elapsed = sample.stop(timer);
            meterRegistry.counter("syncflow.metadata.discovery.count",
                    "type", type, "status", "success").increment();
            return MetadataResponse.of(connectionId, type, result, elapsed / 1_000_000, false);
        } catch (Exception e) {
            sample.stop(timer);
            meterRegistry.counter("syncflow.metadata.discovery.count",
                    "type", type, "status", "error").increment();
            return MetadataResponse.error(connectionId, type, e.getMessage());
        }
    }

    public MetadataResponse<TableMetadata> discoverTables(String connectionId, String schema) {
        var key = connectionId + ":tables:" + schema;
        var cached = cache.getTables(key);
        return cached.map(tableMetadata -> MetadataResponse.of(connectionId, "tables", tableMetadata, 0, true))
                .orElseGet(() -> execute(connectionId, "tables", connector -> {
                    var ctx = context(connectionId);
                    var tables = connector.fetchTables(ctx, schema);
                    cache.putTables(key, tables);
                    return tables;
                }));
    }

    public MetadataResponse<ColumnMetadata> discoverColumns(String connectionId, String schema, String table) {
        var key = connectionId + ":columns:" + schema + "." + table;
        var cached = cache.getColumns(key);
        return cached.map(columnMetadata -> MetadataResponse.of(connectionId, "columns", columnMetadata, 0, true))
                .orElseGet(() -> execute(connectionId, "columns", connector -> {
                    var ctx = context(connectionId);
                    var cols = connector.fetchColumns(ctx, schema, table);
                    cache.putColumns(key, cols);
                    return cols;
                }));
    }

    public MetadataResponse<IndexMetadata> discoverIndexes(String connectionId, String schema, String table) {
        var key = connectionId + ":indexes:" + schema + "." + table;
        var cached = cache.getIndexes(key);
        return cached.map(indexMetadata -> MetadataResponse.of(connectionId, "indexes", indexMetadata, 0, true))
                .orElseGet(() -> execute(connectionId, "indexes", connector -> {
                    var ctx = context(connectionId);
                    var indexes = connector.fetchIndexes(ctx, schema, table);
                    cache.putIndexes(key, indexes);
                    return indexes;
                }));
    }

    public MetadataResponse<ConstraintMetadata> discoverConstraints(String connectionId, String schema, String table) {
        var key = connectionId + ":constraints:" + schema + "." + table;
        var cached = cache.getConstraints(key);
        return cached.map(
                constraintMetadata -> MetadataResponse.of(connectionId, "constraints", constraintMetadata, 0, true))
                .orElseGet(() -> execute(connectionId, "constraints", connector -> {
                    var ctx = context(connectionId);
                    var constraints = connector.fetchConstraints(ctx, schema, table);
                    cache.putConstraints(key, constraints);
                    return constraints;
                }));
    }

    public void refresh(String connectionId) {
        cache.invalidate(connectionId);
    }

    private MetadataCapableConnector resolveConnector(String connectionId) {
        var conn = connectionService.getWithDecryptedCredentials(connectionId);
        var connectorType = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var connector = connectorRegistry.get(connectorType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No metadata connector for type: " + connectorType));
        if (!(connector instanceof MetadataCapableConnector mc)) {
            throw new IllegalArgumentException(
                    "Connector does not support metadata: " + connectorType);
        }
        return mc;
    }

    private ConnectorContext context(String connectionId) {
        var conn = connectionService.getWithDecryptedCredentials(connectionId);
        return new ConnectorContext(toConfig(conn), Map.of());
    }

    private ConnectionConfiguration toConfig(Connection conn) {
        var props = conn.getProperties();
        var creds = conn.getCredentials();
        return new ConnectionConfiguration(
                ConnectorTypeMapper.toCore(props.type()),
                props.host(), props.port(), props.database(),
                creds.username(), creds.password(), props.options());
    }
}
