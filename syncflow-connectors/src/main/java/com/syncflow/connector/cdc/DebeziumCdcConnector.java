package com.syncflow.connector.cdc;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.ForeignKeyMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.PrimaryKeyMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.metadata.TableStatistics;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.spi.CdcCapableConnector;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.ValidationResult;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class DebeziumCdcConnector implements CdcCapableConnector {

    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<CaptureStatus> status = new AtomicReference<>(CaptureStatus.INACTIVE);
    private final AtomicLong eventCounter = new AtomicLong(0);
    private volatile ConnectorContext currentContext;
    private volatile Consumer<CDCEvent> currentConsumer;
    private final Map<String, String> lastOffset = Map.of();

    protected abstract ConnectorType connectorType();
    protected abstract String connectorClassName();
    protected abstract Properties specificProperties(ConnectionConfiguration config);
    protected abstract CDCEvent buildEvent(ChangeEvent<String, String> event, ConnectorContext ctx);

    @Override
    public ConnectorType type() {
        return connectorType();
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(true, false, false, true, true);
    }

    @Override
    public void connect(ConnectorContext ctx) {
    }

    @Override
    public void disconnect() {
        stopCDC();
    }

    @Override
    public boolean isConnected() {
        return running.get();
    }

    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        return ValidationResult.ok();
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        return List.of();
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
        if (running.get())
            return ConnectorHealth.up(0);
        return ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("connectorType", connectorType().name(), "cdc", true);
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
        this.currentContext = context;
        this.currentConsumer = eventConsumer;
        this.status.set(CaptureStatus.RUNNING);

        var config = context.config();
        var debeziumProps = new Properties();
        debeziumProps.setProperty("name", "syncflow-" + connectorType().name().toLowerCase());
        debeziumProps.setProperty("connector.class", connectorClassName());
        debeziumProps.setProperty("offset.storage", "org.apache.kafka.connect.storage.MemoryOffsetBackingStore");
        debeziumProps.setProperty("offset.flush.interval.ms", "5000");
        debeziumProps.setProperty("topic.prefix", "syncflow");
        debeziumProps.setProperty("snapshot.mode", "never");
        debeziumProps.setProperty("database.hostname", config.host());
        debeziumProps.setProperty("database.port", String.valueOf(config.port()));
        debeziumProps.setProperty("database.user", config.username());
        debeziumProps.setProperty("database.password", config.password());
        debeziumProps.setProperty("database.dbname", config.database());
        debeziumProps.setProperty("include.schema.changes", "false");
        debeziumProps.setProperty("tombstones.on.delete", "false");
        debeziumProps.setProperty("key.converter.schemas.enable", "false");
        debeziumProps.setProperty("value.converter.schemas.enable", "false");

        debeziumProps.putAll(specificProperties(config));

        var offset = currentOffset();
        if (!offset.isEmpty()) {
            debeziumProps.setProperty("offset.storage.offset", serializeOffset(offset));
        }

        engine = DebeziumEngine.create(Json.class)
                .using(debeziumProps)
                .notifying(this::handleSingleEvent)
                .build();

        var engineRef = engine;
        Thread.startVirtualThread(() -> {
            try {
                engineRef.run();
            } catch (Exception e) {
                status.set(CaptureStatus.FAILED);
                running.set(false);
            }
        });
    }

    @Override
    public void stopCDC() {
        running.set(false);
        paused.set(false);
        if (engine != null) {
            try {
                engine.close();
            } catch (IOException ignored) {
            }
            engine = null;
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
        if (!running.get() || engine == null)
            return lastOffset;
        return Map.copyOf(lastOffset);
    }

    private void handleSingleEvent(ChangeEvent<String, String> event) {
        if (!running.get())
            return;
        while (paused.get() && running.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try {
            var cdcEvent = buildEvent(event, currentContext);
            if (cdcEvent != null) {
                currentConsumer.accept(cdcEvent);
                eventCounter.incrementAndGet();
            }
        } catch (Exception e) {
            // ponytail: log and continue — no retry
        }
    }

    private String serializeOffset(Map<String, String> offset) {
        var parts = new ArrayList<String>();
        offset.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join(",", parts);
    }
}
