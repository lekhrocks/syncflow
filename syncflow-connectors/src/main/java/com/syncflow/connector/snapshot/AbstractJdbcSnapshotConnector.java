package com.syncflow.connector.snapshot;

import com.syncflow.connector.metadata.AbstractJdbcMetadataConnector;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.SnapshotCapableConnector;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractJdbcSnapshotConnector
        extends
            AbstractJdbcMetadataConnector
        implements
            SnapshotCapableConnector {

    @Override
    public long estimateRows(ConnectorContext ctx, String schema, String table) {
        ensureConnected(ctx);
        // Prefer the planner's reltuples estimate (near-instant, no full scan)
        // over SELECT COUNT(*), which reads the whole table just for progress %.
        var est = estimateFromCatalog(schema, table);
        if (est > 0) {
            return est;
        }
        // Fallback: exact count only when no planner estimate is available.
        var sql = "SELECT COUNT(*) FROM " + schema + "." + table;
        try (var stmt = jdbcConnection.createStatement();
                var rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Postgres planner estimate (reltuples) — cheap, avoids a full table scan. */
    private long estimateFromCatalog(String schema, String table) {
        var sql = "SELECT c.reltuples::bigint FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relname = ?";
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            var rs = stmt.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public Page readBatch(ConnectorContext ctx, String schema, String table,
            BatchInformation batchInfo) {
        ensureConnected(ctx);
        var pkCol = primaryKeyColumn(ctx, schema, table);

        // Keyset (seek) pagination when a single-column PK is available: stable
        // under concurrent writes (no OFFSET drift / duplication) and single-pass.
        if (pkCol != null) {
            return readKeysetPage(ctx, schema, table, batchInfo, pkCol);
        }
        // Fallback: OFFSET/LIMIT for tables without a single-column PK. Not
        // snapshot-isolated, but the cursor is still carried so the executor can
        // resume from the batch.
        return readOffsetPage(schema, table, batchInfo);
    }

    /**
     * Read a page using a keyset cursor over the PK: {@code WHERE pk > :cursor
     * ORDER BY pk LIMIT size}. The last PK value becomes the next cursor, so resume
     * and concurrent writes stay consistent.
     */
    private Page readKeysetPage(ConnectorContext ctx, String schema, String table,
            BatchInformation batchInfo, String pkCol) {
        var cursor = batchInfo.cursor();
        String sql = "SELECT * FROM " + schema + "." + table +
                " WHERE " + pkCol + (cursor == null ? " IS NOT NULL" : " > ?") +
                " ORDER BY " + pkCol +
                " LIMIT " + batchInfo.batchSize();
        var rows = new ArrayList<Map<String, Object>>();
        Object lastPk = null;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            if (cursor != null) {
                // The cursor round-trips through a String (SPI contract). Binding with
                // setObject lets the driver coerce to the PK's column type; lexically
                // the value is compared to a seekable key, which holds for int, bigint,
                // uuid, and text PKs — the types these connectors support.
                stmt.setObject(1, cursor);
            }
            var rs = stmt.executeQuery();
            var meta = rs.getMetaData();
            int pkIndex = columnIndex(meta, pkCol);
            int cols = meta.getColumnCount();
            while (rs.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
                if (pkIndex > 0) {
                    lastPk = rs.getObject(pkIndex);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Keyset batch read failed for " + schema + "." + table, e);
        }
        var nextCursor = !rows.isEmpty() && rows.size() == batchInfo.batchSize()
                ? String.valueOf(lastPk)
                : null;
        return rows.isEmpty() ? Page.empty() : Page.of(rows, nextCursor);
    }

    /** OFFSET/LIMIT fallback for tables without a single-column PK. */
    private Page readOffsetPage(String schema, String table, BatchInformation batchInfo) {
        int offset = batchInfo.cursor() != null
                ? Integer.parseInt(batchInfo.cursor())
                : batchInfo.batchNumber() * batchInfo.batchSize();
        var sql = "SELECT * FROM " + schema + "." + table
                + " OFFSET " + offset + " LIMIT " + batchInfo.batchSize();
        var rows = new ArrayList<Map<String, Object>>();
        try (var stmt = jdbcConnection.createStatement();
                var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Batch read failed for " + schema + "." + table, e);
        }
        var nextOffset = offset + rows.size();
        var nextCursor = rows.size() == batchInfo.batchSize()
                ? String.valueOf(nextOffset)
                : null;
        return rows.isEmpty() ? Page.empty() : Page.of(rows, nextCursor);
    }

    /** Single-column primary key if the table has one, else null. */
    private String primaryKeyColumn(ConnectorContext ctx, String schema, String table) {
        var pk = fetchPrimaryKey(ctx, schema, table);
        return (pk != null && pk.columnNames().size() == 1)
                ? sanitizeIdentifier(pk.columnNames().get(0))
                : null;
    }

    /**
     * Only allow identifiers safe to interpolate into SQL. DB metadata is usually
     * trusted, but a crafted column name must not become an injection vector.
     */
    private static String sanitizeIdentifier(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe column identifier: " + name);
        }
        return name;
    }

    private static int columnIndex(ResultSetMetaData meta, String name) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnName(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
