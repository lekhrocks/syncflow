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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class JdbcBatchWriter implements DestinationWriter {

    /**
     * Identifier allowlist: letters/digits/underscore, must start with letter
     * or underscore. Rejects anything that could be SQL injection (whitespace,
     * quotes, semicolons, comments). Applied to BOTH table names and column
     * names before they are interpolated into raw SQL.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private Connection connection;
    private String currentTable;
    private List<String> currentColumns;
    private List<String> currentUpsertKeys;
    private String currentInsertSql;
    private final List<Map<String, Object>> buffer = new ArrayList<>();
    private final List<Map<String, Object>> deleteBuffer = new ArrayList<>();
    private final List<String> deleteColumns = new ArrayList<>();

    protected abstract String jdbcUrl(ConnectionConfiguration config);
    protected abstract Properties jdbcProperties(ConnectionConfiguration config);

    /** Build a vendor-specific UPSERT statement (e.g. ON CONFLICT). */
    protected String upsertSql(String table, List<String> columns, List<String> keyColumns) {
        var safeTable = sanitizeIdentifier(table);
        var safeCols = sanitizeIdentifiers(columns);
        var safeKeys = sanitizeIdentifiers(keyColumns);
        var cols = String.join(", ", safeCols);
        var params = "?" + ", ?".repeat(safeCols.size() - 1);
        return "INSERT INTO " + safeTable + " (" + cols + ") VALUES (" + params + ")"
                + " ON CONFLICT (" + String.join(", ", safeKeys) + ")"
                + " DO UPDATE SET " + safeCols.stream()
                        .filter(c -> !safeKeys.contains(c))
                        .map(c -> c + " = EXCLUDED." + c)
                        .collect(Collectors.joining(", "));
    }

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
    public void writeBatch(String table, List<String> columns, List<Map<String, Object>> rows) {
        if (rows.isEmpty())
            return;
        var safeTable = sanitizeIdentifier(table);
        var safeColumns = sanitizeIdentifiers(columns);
        // R8: guard against buffer corruption. If the caller issues writeBatch
        // for table A then table B before flushing, the shared buffer would mix
        // A's rows with B's SQL. Flush any prior buffered rows first so each
        // writeBatch call is self-contained with its own table/columns.
        // The flush is connection-null-safe: with no open connection the buffer
        // is reset rather than left to leak across the table boundary.
        if (!buffer.isEmpty()) {
            if (currentTable == null || !currentTable.equals(safeTable)
                    || currentColumns == null || !currentColumns.equals(safeColumns)) {
                flushInserts();
                if (!buffer.isEmpty())
                    buffer.clear();
            }
        }
        currentTable = safeTable;
        currentColumns = safeColumns;
        currentInsertSql = null; // rebuilt on flush from currentTable/currentColumns
        buffer.addAll(rows);
        if (buffer.size() >= 1000) {
            flushInserts();
        }
    }

    @Override
    public void upsertBatch(String table, List<String> columns, List<Map<String, Object>> rows,
            List<String> keyColumns) {
        if (rows.isEmpty())
            return;
        var safeTable = sanitizeIdentifier(table);
        var safeColumns = sanitizeIdentifiers(columns);
        var safeKeys = sanitizeIdentifiers(keyColumns);
        if (!buffer.isEmpty()) {
            if (currentTable == null || !currentTable.equals(safeTable)
                    || currentUpsertKeys == null || !currentUpsertKeys.equals(safeKeys)) {
                // Different table or key columns than what's buffered — flush first.
                flushInserts();
                if (!buffer.isEmpty())
                    buffer.clear();
            }
        }
        currentTable = safeTable;
        currentColumns = safeColumns;
        currentUpsertKeys = safeKeys;
        currentInsertSql = upsertSql(safeTable, safeColumns, safeKeys);
        buffer.addAll(rows);
        if (buffer.size() >= 1000) {
            flushInserts();
        }
    }

    @Override
    public void deleteBatch(String table, List<String> pkColumns, List<Map<String, Object>> pks) {
        if (pks.isEmpty())
            return;
        var safeTable = sanitizeIdentifier(table);
        var safePkColumns = sanitizeIdentifiers(pkColumns);
        // R8: deletes must not share a buffer across tables. Flush a prior
        // delete batch when the table or PK columns differ. Connection-null-safe.
        if (!deleteBuffer.isEmpty()) {
            if (currentTable == null || !currentTable.equals(safeTable)
                    || !deleteColumns.equals(safePkColumns)) {
                flushDeletes();
                if (!deleteBuffer.isEmpty())
                    deleteBuffer.clear();
            }
        }
        currentTable = safeTable;
        deleteColumns.clear();
        deleteColumns.addAll(safePkColumns);
        deleteBuffer.addAll(pks);
        if (deleteBuffer.size() >= 1000) {
            flushDeletes();
        }
    }

    @Override
    public void flush() {
        if (buffer.isEmpty() && deleteBuffer.isEmpty())
            return;
        if (!buffer.isEmpty())
            flushInserts();
        if (!deleteBuffer.isEmpty())
            flushDeletes();
    }

    private void flushInserts() {
        if (connection == null || buffer.isEmpty())
            return;
        try {
            // Columns come from the sanitized value stored at writeBatch time,
            // not re-derived from row keys on flush (which would be unsanitized).
            var columns = currentColumns != null
                    ? currentColumns
                    : sanitizeIdentifiers(new ArrayList<>(buffer.getFirst().keySet()));
            var sql = currentInsertSql != null ? currentInsertSql : buildInsertSql(columns, currentTable);
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
            currentColumns = null;
            currentUpsertKeys = null;
            currentInsertSql = null;
        } catch (SQLException e) {
            throw new RuntimeException("Batch write failed", e);
        }
    }

    private void flushDeletes() {
        if (connection == null || deleteBuffer.isEmpty())
            return;
        try {
            var sql = buildDeleteSql(currentTable, deleteColumns, deleteBuffer.size());
            try (var stmt = connection.prepareStatement(sql)) {
                int idx = 1;
                for (var pk : deleteBuffer) {
                    for (var col : deleteColumns) {
                        stmt.setObject(idx++, pk.get(col));
                    }
                }
                stmt.executeUpdate();
            }
            deleteBuffer.clear();
            deleteColumns.clear();
        } catch (SQLException e) {
            throw new RuntimeException("Batch delete failed", e);
        }
    }

    /**
     * Build {@code DELETE FROM t WHERE (cols) IN ((?, ...), ...)}.
     * Identifiers are pre-sanitized by the callers, but we re-sanitize here so
     * the SQL builder itself is safe regardless of caller. Works for single and
     * composite PKs via the composite IN-list syntax.
     */
    protected String buildDeleteSql(String table, List<String> pkColumns, int numRows) {
        var safeTable = sanitizeIdentifier(table);
        var safePkCols = sanitizeIdentifiers(pkColumns);
        var pkCols = String.join(", ", safePkCols);
        var placeholders = "(" + "?, ".repeat(safePkCols.size() - 1) + "?)";
        var tuples = placeholders + (", " + placeholders).repeat(numRows - 1);
        return "DELETE FROM " + safeTable + " WHERE (" + pkCols + ") IN (" + tuples + ")";
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

    /** Borrow the underlying connection. Pooled writers override this. */
    public Connection getConnection() throws SQLException {
        return connection;
    }

    /**
     * The table currently tracked as pending (or null). Package-private for tests.
     */
    String pendingTable() {
        return currentTable;
    }

    private String buildInsertSql(List<String> columns, String table) {
        var cols = String.join(", ", columns);
        var params = "?" + ", ?".repeat(columns.size() - 1);
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + params + ")";
    }

    private static String sanitizeIdentifier(String name) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + name);
        }
        return name;
    }

    private static List<String> sanitizeIdentifiers(List<String> names) {
        var out = new ArrayList<String>(names.size());
        for (var n : names) {
            out.add(sanitizeIdentifier(n));
        }
        return out;
    }
}
