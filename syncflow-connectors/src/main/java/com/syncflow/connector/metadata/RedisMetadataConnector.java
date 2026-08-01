package com.syncflow.connector.metadata;

import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.ForeignKeyMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.PrimaryKeyMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.metadata.TableStatistics;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.SnapshotCapableConnector;
import com.syncflow.core.spi.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RedisMetadataConnector implements SnapshotCapableConnector {

    // ponytail: Redis metadata uses basic socket-level info.
    // Full Redis driver integration (Jedis/Lettuce) deferred until Redis connector
    // implementation.
    // For now return structure metadata from configuration without live connection.

    @Override
    public ConnectorType type() {
        return ConnectorType.REDIS;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(false, false, false, false, false);
    }

    @Override
    public void connect(ConnectorContext ctx) {
        /* Redis driver stub */ }

    @Override
    public void disconnect() {
        /* Redis driver stub */ }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        return ValidationResult.ok();
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        return List.of("db0");
    }

    @Override
    public List<TableMetadata> fetchTables(ConnectorContext ctx, String schema) {
        return List.of();
    }

    @Override
    public List<ColumnMetadata> fetchColumns(ConnectorContext ctx, String schema, String table) {
        return List.of();
    }

    @Override
    public List<IndexMetadata> fetchIndexes(ConnectorContext ctx, String schema, String table) {
        return List.of();
    }

    @Override
    public PrimaryKeyMetadata fetchPrimaryKey(ConnectorContext ctx, String schema, String table) {
        return null;
    }

    @Override
    public List<ForeignKeyMetadata> fetchForeignKeys(ConnectorContext ctx, String schema, String table) {
        return List.of();
    }

    @Override
    public List<ConstraintMetadata> fetchConstraints(ConnectorContext ctx, String schema, String table) {
        return List.of();
    }

    @Override
    public TableStatistics fetchStatistics(ConnectorContext ctx, String schema, String table) {
        return TableStatistics.unknown();
    }

    @Override
    public ConnectorHealth health() {
        return ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "7+", "vendor", "Redis");
    }

    @Override
    public long estimateRows(ConnectorContext ctx, String schema, String table) {
        return 0;
    }

    @Override
    public Page readBatch(ConnectorContext ctx, String schema, String table,
            BatchInformation batchInfo) {
        return Page.empty();
    }
}
