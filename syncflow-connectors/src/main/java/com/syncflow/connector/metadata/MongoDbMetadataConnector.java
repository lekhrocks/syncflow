package com.syncflow.connector.metadata;

import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.DataType;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MongoDbMetadataConnector implements SnapshotCapableConnector {

    private com.mongodb.client.MongoClient client;

    @Override
    public ConnectorType type() {
        return ConnectorType.MONGODB;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(false, true, true, false, false);
    }

    @Override
    public void connect(ConnectorContext ctx) {
        disconnect();
        var uri = "mongodb://" + ctx.config().username() + ":" + ctx.config().password()
                + "@" + ctx.config().host() + ":" + ctx.config().port()
                + "/" + ctx.config().database();
        client = com.mongodb.client.MongoClients.create(uri);
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Override
    public boolean isConnected() {
        try {
            if (client != null) {
                client.listDatabaseNames().first();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        try {
            connect(ctx);
            client.listDatabaseNames().first();
            return ValidationResult.ok();
        } catch (Exception e) {
            return ValidationResult.failed(List.of(e.getMessage()));
        } finally {
            disconnect();
        }
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        ensureConnected(ctx);
        return client.listDatabaseNames().into(new ArrayList<>());
    }

    @Override
    public List<TableMetadata> fetchTables(ConnectorContext ctx, String schema) {
        ensureConnected(ctx);
        var tables = new ArrayList<TableMetadata>();
        var db = client.getDatabase(schema);
        for (var name : db.listCollectionNames()) {
            var estimatedCount = db.getCollection(name).estimatedDocumentCount();
            var stats = new TableStatistics(estimatedCount, 0, 0, 0, 0, 0);
            tables.add(new TableMetadata(name, "COLLECTION", schema, null,
                    stats, List.of(), List.of(), null, List.of(), List.of()));
        }
        return tables;
    }

    @Override
    public List<ColumnMetadata> fetchColumns(ConnectorContext ctx, String schema, String collection) {
        ensureConnected(ctx);
        var columns = new LinkedHashMap<String, ColumnMetadata>();
        var db = client.getDatabase(schema);
        var col = db.getCollection(collection);

        // ponytail: sample up to 100 documents to infer field types
        var sample = col.find().limit(100).into(new ArrayList<>());
        for (var doc : sample) {
            for (var key : doc.keySet()) {
                columns.putIfAbsent(key, inferColumn(key, doc.get(key)));
            }
        }
        return List.copyOf(columns.values());
    }

    @Override
    public List<IndexMetadata> fetchIndexes(ConnectorContext ctx, String schema, String collection) {
        ensureConnected(ctx);
        var indexes = new ArrayList<IndexMetadata>();
        var db = client.getDatabase(schema);
        for (var idx : db.getCollection(collection).listIndexes()) {
            var name = idx.getString("name");
            var unique = idx.getBoolean("unique", false);
            var keyDoc = (org.bson.Document) idx.get("key");
            var cols = new ArrayList<String>();
            if (keyDoc != null) {
                cols.addAll(keyDoc.keySet());
            }
            indexes.add(new IndexMetadata(name, cols, unique, "_id_".equals(name), "BTREE"));
        }
        return indexes;
    }

    @Override
    public PrimaryKeyMetadata fetchPrimaryKey(ConnectorContext ctx, String schema, String table) {
        return new PrimaryKeyMetadata("_id_", List.of("_id"));
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
    public TableStatistics fetchStatistics(ConnectorContext ctx, String schema, String collection) {
        ensureConnected(ctx);
        try {
            var db = client.getDatabase(schema);
            var count = db.getCollection(collection).estimatedDocumentCount();
            return new TableStatistics(count, 0, 0, 0, 0, 0);
        } catch (Exception e) {
            return TableStatistics.unknown();
        }
    }

    @Override
    public ConnectorHealth health() {
        try {
            if (client != null) {
                client.listDatabaseNames().first();
                return ConnectorHealth.up(0);
            }
        } catch (Exception ignored) {
        }
        return ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "7.0+", "vendor", "MongoDB");
    }

    @Override
    public long estimateRows(ConnectorContext ctx, String schema, String collection) {
        ensureConnected(ctx);
        try {
            return client.getDatabase(schema).getCollection(collection).estimatedDocumentCount();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Page readBatch(ConnectorContext ctx, String schema, String collection,
            BatchInformation batchInfo) {
        ensureConnected(ctx);
        var db = client.getDatabase(schema);
        var col = db.getCollection(collection);
        var skip = (long) batchInfo.batchNumber() * batchInfo.batchSize();
        var rows = new ArrayList<Map<String, Object>>();
        for (var doc : col.find().skip((int) skip).limit(batchInfo.batchSize())) {
            rows.add(doc.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)));
        }
        var nextCursor = rows.size() == batchInfo.batchSize()
                ? String.valueOf(skip + batchInfo.batchSize())
                : null;
        return rows.isEmpty() ? Page.empty() : Page.of(rows, nextCursor);
    }

    private void ensureConnected(ConnectorContext ctx) {
        if (!isConnected())
            connect(ctx);
    }

    private ColumnMetadata inferColumn(String key, Object value) {
        var nativeType = value != null ? value.getClass().getSimpleName() : "unknown";
        return new ColumnMetadata(key, 0,
                new DataType(nativeType, nativeType, null, null, true, null),
                "_id".equals(key), false, "_id".equals(key), false, null);
    }
}
