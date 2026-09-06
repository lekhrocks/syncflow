package com.syncflow.api.plugin.adapter;

import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.plugin.spi.DestinationWriterProvider;
import com.syncflow.plugin.spi.PluginContext;

import java.util.List;
import java.util.Map;

/**
 * Adapts a plugin's {@link DestinationWriterProvider} to the core
 * {@link DestinationWriter} contract.
 *
 * <p>
 * Core's writeBatch/deleteBatch/upsertBatch take an explicit column
 * list; the plugin SPI only knows about the row maps. We project rows
 * to the named columns, dropping any keys the plugin returned that the
 * pipeline didn't ask for.
 *
 * <p>
 * The plugin SPI has no DELETE or UPSERT; the default implementations
 * on {@link DestinationWriterProvider} are no-ops for delete and a plain
 * {@code write} (key columns ignored) for upsert. Plugins that want
 * proper DELETE/UPSERT semantics override those defaults.
 */
public final class PluginWriterAdapter implements DestinationWriter {

    private final DestinationWriterProvider delegate;
    private final ConnectionConfiguration config;
    private boolean connected;

    public PluginWriterAdapter(DestinationWriterProvider delegate,
            ConnectionConfiguration config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public void connect(ConnectionConfiguration cfg) {
        var pluginCtx = new PluginContext(
                cfg.host(), cfg.port(), cfg.database(),
                cfg.username(), cfg.password(), cfg.properties());
        delegate.connect(pluginCtx);
        connected = true;
    }

    @Override
    public void writeBatch(String table, List<String> columns, List<Map<String, Object>> rows) {
        delegate.write(table, project(columns, rows));
    }

    @Override
    public void deleteBatch(String table, List<String> columns, List<Map<String, Object>> rows) {
        // No-op by default in the plugin SPI. Override to support DELETE.
        delegate.delete(table, project(columns, rows));
    }

    @Override
    public void upsertBatch(String table, List<String> columns, List<Map<String, Object>> rows,
            List<String> keyColumns) {
        delegate.upsert(table, project(columns, rows), keyColumns);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void commit() {
        delegate.commit();
    }

    @Override
    public void rollback() {
        delegate.rollback();
    }

    @Override
    public void close() {
        delegate.close();
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    private static List<Map<String, Object>> project(List<String> columns,
            List<Map<String, Object>> rows) {
        if (columns == null || columns.isEmpty()) {
            return rows;
        }
        var projected = new java.util.ArrayList<Map<String, Object>>(rows.size());
        for (var row : rows) {
            var out = new java.util.LinkedHashMap<String, Object>(columns.size());
            for (var col : columns) {
                out.put(col, row.get(col));
            }
            projected.add(out);
        }
        return projected;
    }
}
