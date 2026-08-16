package com.syncflow.core.spi.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import java.util.List;
import java.util.Map;

/**
 * SPI for writing rows to a destination (JDBC, Kafka, S3, etc.).
 *
 * Convention: all batch operations take {@code (table, columns, values)}.
 * For deletes, the columns are the primary-key columns and the values are
 * the corresponding PK maps. Standardising the parameter order reduces
 * the cognitive load on connector implementors and callers.
 */
public interface DestinationWriter extends AutoCloseable {

    void connect(ConnectionConfiguration config);

    /**
     * Insert rows into {@code table}. {@code columns} lists destination columns;
     * each row map's keys are matched against it.
     */
    void writeBatch(String table, List<String> columns, List<Map<String, Object>> rows);

    /**
     * Delete rows from {@code table} where the values in {@code columns} match
     * the corresponding keys in each row map. Emits a single batched
     * {@code DELETE WHERE (cols) IN (...)} when the underlying connector supports
     * it.
     */
    void deleteBatch(String table, List<String> columns, List<Map<String, Object>> rows);

    /** Upsert: insert or update rows keyed by {@code keyColumns}. */
    default void upsertBatch(String table, List<String> columns, List<Map<String, Object>> rows,
            List<String> keyColumns) {
        // Default impl falls back to plain insert. Connector implementations
        // (Postgres/Mysql) override with ON CONFLICT / ON DUPLICATE KEY UPDATE.
        writeBatch(table, columns, rows);
    }

    void flush();

    void commit();

    void rollback();

    @Override
    void close();

    boolean isConnected();
}
