package com.syncflow.connector.cdc;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.ForeignKeyMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.PrimaryKeyMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.metadata.TableStatistics;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.spi.CdcCapableConnector;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.ValidationResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class MongoDbCdcConnector implements CdcCapableConnector {

    private com.mongodb.client.MongoClient client;
    private Thread captureThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<CaptureStatus> status = new AtomicReference<>(CaptureStatus.INACTIVE);
    private BsonDocument resumeToken;

    @Override
    public ConnectorType type() {
        return ConnectorType.MONGODB;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(true, false, false, false, true);
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
        stopCDC();
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && running.get();
    }

    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        return ValidationResult.ok();
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        return List.of(ctx.config().database());
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
        return running.get() ? ConnectorHealth.up(0) : ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("connectorType", "MONGODB", "cdc", true);
    }

    @Override
    public long estimateRows(ConnectorContext ctx, String schema, String table) {
        return 0;
    }

    @Override
    public Page readBatch(ConnectorContext ctx, String schema, String table, BatchInformation bi) {
        return Page.empty();
    }

    @Override
    public void startCDC(ConnectorContext context, Consumer<CDCEvent> eventConsumer) {
        if (running.getAndSet(true))
            return;
        status.set(CaptureStatus.RUNNING);

        captureThread = Thread.startVirtualThread(() -> {
            var db = client.getDatabase(context.config().database());
            try (var cursor = db.watch()
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .resumeAfter(resumeToken)
                    .cursor()) {
                while (cursor.hasNext() && running.get()) {
                    while (paused.get() && running.get()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    var doc = cursor.next();
                    var event = toCdcEvent(doc, context);
                    if (event != null)
                        eventConsumer.accept(event);
                    resumeToken = doc.getResumeToken();
                }
            } catch (Exception e) {
                if (running.get())
                    status.set(CaptureStatus.FAILED);
            }
        });
    }

    @Override
    public void stopCDC() {
        running.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        status.set(CaptureStatus.INACTIVE);
    }

    @Override
    public void pauseCDC() {
        paused.set(true);
        status.set(CaptureStatus.PAUSED);
    }

    @Override
    public void resumeCDC() {
        paused.set(false);
        status.set(CaptureStatus.RUNNING);
    }

    @Override
    public boolean isCdcActive() {
        return running.get();
    }

    @Override
    public CaptureStatus captureStatus() {
        return status.get();
    }

    @Override
    public Map<String, String> currentOffset() {
        if (resumeToken == null)
            return Map.of();
        return Map.of("resumeToken", resumeToken.toJson());
    }

    private CDCEvent toCdcEvent(ChangeStreamDocument<Document> doc, ConnectorContext ctx) {
        var op = switch (doc.getOperationType()) {
            case INSERT -> CDCOperation.INSERT;
            case UPDATE, REPLACE -> CDCOperation.UPDATE;
            case DELETE -> CDCOperation.DELETE;
            default -> null;
        };
        if (op == null)
            return null;

        var ns = doc.getNamespace();
        var dbName = ns != null ? ns.getDatabaseName() : ctx.config().database();
        var collName = ns != null ? ns.getCollectionName() : "";

        Map<String, Object> after = null;
        Map<String, Object> before = null;
        if (doc.getFullDocument() != null) {
            after = doc.getFullDocument().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
        var id = doc.getDocumentKey();
        Map<String, Object> pk = id != null ? Map.of("_id", id.toJson()) : Map.of("_id", "unknown");

        return new CDCEvent(
                new EventHeader(UUID.randomUUID().toString(), ctx.config().database(),
                        ctx.config().host(), System.currentTimeMillis(), 1, Map.of()),
                new EventSource(dbName, dbName, collName, "mongodb"),
                op, new EventPayload(before, after, pk),
                new EventMetadata(0, Instant.now(), 0), null,
                new OffsetInformation("MONGODB", currentOffset(), "", Instant.now()));
    }
}
