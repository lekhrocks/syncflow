package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.spi.writer.DestinationWriter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public abstract class JdbcBatchWriter implements DestinationWriter {

    private Connection connection;
    private String currentTable;
    private String currentInsertSql;
    private final List<Map<String, Object>> buffer = new ArrayList<>();

    protected abstract String jdbcUrl(ConnectionConfiguration config);
    protected abstract Properties jdbcProperties(ConnectionConfiguration config);

    @Override
    public void connect(ConnectionConfiguration config) {
        try {
            connection = DriverManager.getConnection(jdbcUrl(config), jdbcProperties(config));
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect writer", e);
        }
    }

    @Override
    public void writeBatch(String table, List<Map<String, Object>> rows, List<String> columns) {
        if (rows.isEmpty())
            return;
        buffer.addAll(rows);
        if (buffer.size() >= 1000) {
            flush();
        }
    }

    @Override
    public void flush() {
        if (buffer.isEmpty() || connection == null)
            return;
        try {
            var columns = new ArrayList<>(buffer.getFirst().keySet());
            var sql = buildInsertSql(columns);
            try (var stmt = connection.prepareStatement(sql)) {
                for (var row : buffer) {
                    for (int i = 0; i < columns.size(); i++) {
                        stmt.setObject(i + 1, row.get(columns.get(i)));
                    }
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            buffer.clear();
        } catch (SQLException e) {
            throw new RuntimeException("Batch write failed", e);
        }
    }

    @Override
    public void commit() {
        try {
            if (connection != null)
                connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Commit failed", e);
        }
    }

    @Override
    public void rollback() {
        try {
            if (connection != null)
                connection.rollback();
        } catch (SQLException e) {
            throw new RuntimeException("Rollback failed", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private String buildInsertSql(List<String> columns) {
        var cols = String.join(", ", columns);
        var params = "?" + ", ?".repeat(columns.size() - 1);
        return "INSERT INTO " + currentTable + " (" + cols + ") VALUES (" + params + ")";
    }
}
