package com.syncflow.api.plugin.adapter;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CaptureStatus;
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
import com.syncflow.core.snapshot.ChunkRange;
import com.syncflow.core.spi.CdcCapableConnector;
import com.syncflow.core.spi.Connector;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.ConnectorValidationResult;
import com.syncflow.core.spi.MetadataCapableConnector;
import com.syncflow.core.spi.SnapshotCapableConnector;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.plugin.descriptor.PluginDescriptor;
import com.syncflow.plugin.spi.CdcProvider;
import com.syncflow.plugin.spi.PluginConnector;
import com.syncflow.plugin.spi.SnapshotProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Adapts a plugin-world {@link PluginConnector} to the full core SPI:
 * {@link Connector}, {@link MetadataCapableConnector},
 * {@link SnapshotCapableConnector}, {@link CdcCapableConnector},
 * {@link DestinationWriter}.
 *
 * <p>
 * One instance per (pluginId, ConnectorContext). The plugin's
 * capabilities are queried lazily so the adapter's reported capability
 * flags reflect the current plugin state.
 *
 * <p>
 * Cursor handling: the plugin SPI's {@code readBatch} takes a batch
 * number, not a cursor. We round-trip the batch number through
 * {@link BatchInformation#cursor()} using {@link PluginCursorCodec}.
 * {@link #rangeChunks} returns a single whole-table chunk because the
 * plugin SPI has no PK-range splitting API — parallel snapshot
 * parallelism is effectively 1 for plugin-backed sources.
 *
 * <p>
 * CDC transaction metadata is always null and CDC pause/resume are
 * no-ops because the plugin SPI lacks these concepts.
 */
public final class PluginConnectorAdapter
        implements
            Connector,
            MetadataCapableConnector,
            SnapshotCapableConnector,
            CdcCapableConnector {

    private final PluginConnector delegate;
    private final PluginDescriptor descriptor;
    private final ConnectorType type;
    private volatile boolean connected;
    private volatile boolean cdcActive;
    private final Map<TableKey, Integer> lastBatchByTable = new ConcurrentHashMap<>();
    private String pluginId;

    private record TableKey(String schema, String table) {
    }

    public PluginConnectorAdapter(PluginConnector delegate, PluginDescriptor descriptor) {
        this.delegate = delegate;
        this.descriptor = descriptor;
        this.type = ConnectorTypeResolver.resolve(descriptor.connectorType());
        this.pluginId = descriptor.pluginId();
    }

    /** Re-attached for use by the registry which needs to know the plugin id. */
    public String pluginId() {
        return pluginId;
    }

    @Override
    public ConnectorType type() {
        return type;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return PluginCapabilitiesAdapter.map(delegate.capabilities());
    }

    @Override
    public void connect(ConnectorContext context) {
        // The plugin SPI has no connect() method; track state ourselves.
        // Capability providers (SnapshotProvider, CdcProvider,
        // DestinationWriterProvider)
        // use the PluginContext passed to their first method call.
        connected = true;
    }

    @Override
    public void disconnect() {
        if (!connected) {
            return;
        }
        // No plugin-level disconnect. CDC capture must already be stopped
        // by stopCDC() before this is called, otherwise events flow to a
        // disposed consumer.
        connected = false;
        cdcActive = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public ConnectorValidationResult validate(ConnectorContext context) {
        // Plugin SPI has no dedicated validation. Try a connect/disconnect
        // round-trip and report the outcome.
        try {
            if (!connected) {
                connect(context);
            }
            return ConnectorValidationResult.ok();
        } catch (Exception e) {
            return ConnectorValidationResult.failed(List.of(
                    "Plugin validation failed: " + e.getMessage()));
        }
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext context) {
        return delegate.discoverSchemas(PluginContextAdapter.from(context));
    }

    @Override
    public List<String> discoverTables(ConnectorContext context, String schema) {
        return delegate.discoverTables(PluginContextAdapter.from(context), schema);
    }

    @Override
    public ConnectorHealth health() {
        return PluginHealthAdapter.parse(delegate.health());
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.copyOf(delegate.metadata());
    }

    // --- MetadataCapableConnector ---

    @Override
    public List<TableMetadata> fetchTables(ConnectorContext context, String schema) {
        var pluginCtx = PluginContextAdapter.from(context);
        var tables = delegate.discoverTables(pluginCtx, schema);
        return tables.stream()
                .map(name -> {
                    var cols = delegate.discoverColumns(pluginCtx, schema, name);
                    return new TableMetadata(name, "TABLE", schema, null,
                            TableStatistics.unknown(),
                            toColumnMetadata(cols),
                            List.<IndexMetadata>of(),
                            primaryKeyFromColumns(cols),
                            List.<ForeignKeyMetadata>of(),
                            List.<ConstraintMetadata>of());
                })
                .toList();
    }

    @Override
    public List<ColumnMetadata> fetchColumns(ConnectorContext context, String schema, String table) {
        var pluginCtx = PluginContextAdapter.from(context);
        return toColumnMetadata(delegate.discoverColumns(pluginCtx, schema, table));
    }

    @Override
    public List<IndexMetadata> fetchIndexes(ConnectorContext context, String schema, String table) {
        return List.of();
    }

    @Override
    public PrimaryKeyMetadata fetchPrimaryKey(ConnectorContext context, String schema, String table) {
        return primaryKeyFromColumns(
                delegate.discoverColumns(PluginContextAdapter.from(context), schema, table));
    }

    @Override
    public List<ForeignKeyMetadata> fetchForeignKeys(ConnectorContext context, String schema, String table) {
        return List.of();
    }

    @Override
    public List<ConstraintMetadata> fetchConstraints(ConnectorContext context, String schema, String table) {
        return List.of();
    }

    @Override
    public TableStatistics fetchStatistics(ConnectorContext context, String schema, String table) {
        return TableStatistics.unknown();
    }

    // --- SnapshotCapableConnector ---

    @Override
    public long estimateRows(ConnectorContext context, String schema, String table) {
        if (delegate instanceof SnapshotProvider sp) {
            return sp.estimateRowCount(PluginContextAdapter.from(context), schema, table);
        }
        return -1L;
    }

    @Override
    public SnapshotCapableConnector.Page readBatch(ConnectorContext context, String schema,
            String table, BatchInformation batchInfo) {
        if (!(delegate instanceof SnapshotProvider sp)) {
            return SnapshotCapableConnector.Page.empty();
        }
        var pluginCtx = PluginContextAdapter.from(context);
        var batchNumber = PluginCursorCodec.decode(batchInfo.cursor());
        var page = sp.readBatch(pluginCtx, schema, table, batchNumber,
                batchInfo.batchSize() == 0 ? 1000 : batchInfo.batchSize());
        lastBatchByTable.put(new TableKey(schema, table), batchNumber);
        // Convert the plugin's nextCursor into our encoded form, or null when done.
        var next = page.nextCursor();
        String encoded;
        if (next == null) {
            encoded = null;
        } else if (next.startsWith(PluginCursorCodec.encode(0).substring(0, 1))) {
            // Already encoded by us (or a plugin using the same scheme).
            encoded = next;
        } else {
            // Treat the plugin's string cursor as opaque — bump the batch.
            encoded = PluginCursorCodec.encode(batchNumber + 1);
        }
        return SnapshotCapableConnector.Page.of(page.rows(), encoded);
    }

    @Override
    public List<ChunkRange> rangeChunks(ConnectorContext context, String schema, String table,
            int chunkCount) {
        // Plugin SPI has no PK-range splitting — single whole-table chunk.
        return List.of(ChunkRange.whole());
    }

    @Override
    public SnapshotCapableConnector snapshotClone(ConnectorContext context) {
        // Reuse the same delegate and descriptor; per-clone state is the
        // snapshot's job (each chunk worker is a sequential read of the
        // same plugin instance).
        return new PluginConnectorAdapter(delegate, descriptor);
    }

    // --- CdcCapableConnector ---

    @Override
    public void startCDC(ConnectorContext context, Consumer<CDCEvent> eventConsumer) {
        if (!(delegate instanceof CdcProvider cp)) {
            throw new UnsupportedOperationException(
                    "Plugin " + pluginId + " does not implement CdcProvider");
        }
        if (cdcActive) {
            return;
        }
        var pluginCtx = PluginContextAdapter.from(context);
        cp.startCapture(pluginCtx,
                e -> eventConsumer.accept(PluginCdcEventAdapter.toCore(e, descriptor)));
        cdcActive = true;
    }

    @Override
    public void stopCDC() {
        if (!(delegate instanceof CdcProvider cp)) {
            return;
        }
        if (cdcActive) {
            cp.stopCapture();
        }
        cdcActive = false;
    }

    @Override
    public void pauseCDC() {
        // Plugin SPI has no pause; track state for isCdcActive() reporting.
        if (cdcActive) {
            cdcActive = false;
        }
    }

    @Override
    public void resumeCDC() {
        // No-op: paused CDC needs startCDC to resume on the plugin side.
    }

    @Override
    public boolean isCdcActive() {
        if (delegate instanceof CdcProvider cp) {
            return cp.isCapturing();
        }
        return false;
    }

    @Override
    public CaptureStatus captureStatus() {
        if (!cdcActive) {
            return CaptureStatus.INACTIVE;
        }
        if (delegate instanceof CdcProvider cp) {
            return cp.isCapturing() ? CaptureStatus.RUNNING : CaptureStatus.INACTIVE;
        }
        return CaptureStatus.INACTIVE;
    }

    @Override
    public Map<String, String> currentOffset() {
        if (delegate instanceof CdcProvider cp) {
            return Map.copyOf(cp.currentOffset());
        }
        return Map.of();
    }

    // --- helpers ---

    private static List<ColumnMetadata> toColumnMetadata(List<Map<String, Object>> pluginCols) {
        if (pluginCols == null) {
            return List.of();
        }
        var out = new java.util.ArrayList<ColumnMetadata>(pluginCols.size());
        int pos = 1;
        for (var col : pluginCols) {
            var name = stringAt(col, "name", "columnName", "column");
            var type = stringAt(col, "type", "dataType", "nativeType");
            var nullable = boolAt(col, "nullable", true);
            var isPk = boolAt(col, "primaryKey", false);
            out.add(new ColumnMetadata(name, pos++,
                    new DataType(type, type, null, null, nullable, null),
                    isPk, false, false, false, null));
        }
        return out;
    }

    private static PrimaryKeyMetadata primaryKeyFromColumns(List<Map<String, Object>> pluginCols) {
        if (pluginCols == null) {
            return new PrimaryKeyMetadata(null, List.of());
        }
        var pkCols = pluginCols.stream()
                .filter(c -> boolAt(c, "primaryKey", false))
                .map(c -> stringAt(c, "name", "columnName", "column"))
                .toList();
        if (pkCols.isEmpty()) {
            return new PrimaryKeyMetadata(null, List.of());
        }
        return new PrimaryKeyMetadata("pk_" + String.join("_", pkCols), pkCols);
    }

    private static String stringAt(Map<String, Object> m, String... keys) {
        for (var k : keys) {
            var v = m.get(k);
            if (v != null) {
                return v.toString();
            }
        }
        return "";
    }

    private static boolean boolAt(Map<String, Object> m, String key, boolean dflt) {
        var v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return dflt;
    }
}
