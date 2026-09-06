package com.syncflow.plugin.spi;

import java.util.List;
import java.util.Map;

public interface DestinationWriterProvider extends AutoCloseable {

    void connect(PluginContext context);

    void write(String table, List<Map<String, Object>> rows);

    /**
     * Delete rows. Default is a no-op; plugins with native DELETE support
     * should override. Returning silently (rather than throwing) is a
     * deliberate backward-compat choice: the adapter degrades gracefully
     * for plugins that pre-date this method.
     */
    default void delete(String table, List<Map<String, Object>> rows) {
        // no-op default — see javadoc
    }

    /**
     * Upsert rows keyed by {@code keyColumns}. Default falls back to
     * {@link #write(String, List)}; the keys are ignored. Plugins with
     * native upsert semantics (ON CONFLICT, ON DUPLICATE KEY) override.
     */
    default void upsert(String table, List<Map<String, Object>> rows,
            List<String> keyColumns) {
        write(table, rows);
    }

    void flush();

    void commit();

    void rollback();

    @Override
    void close();
}
