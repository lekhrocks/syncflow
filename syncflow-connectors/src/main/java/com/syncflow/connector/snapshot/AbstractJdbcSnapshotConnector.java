package com.syncflow.connector.snapshot;

import com.syncflow.connector.metadata.AbstractJdbcMetadataConnector;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.SnapshotCapableConnector;

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
        var sql = "SELECT COUNT(*) FROM " + schema + "." + table;
        try (var stmt = jdbcConnection.createStatement();
                var rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public Page readBatch(ConnectorContext ctx, String schema, String table,
            BatchInformation batchInfo) {
        ensureConnected(ctx);
        int offset = batchInfo.batchNumber() * batchInfo.batchSize();
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
        var nextCursor = rows.size() == batchInfo.batchSize()
                ? String.valueOf(offset + batchInfo.batchSize())
                : null;
        return rows.isEmpty() ? Page.empty() : Page.of(rows, nextCursor);
    }
}
