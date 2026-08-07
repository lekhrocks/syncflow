package com.syncflow.connector.metadata;

import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.DataType;
import com.syncflow.core.metadata.ForeignKeyMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.PrimaryKeyMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.metadata.TableStatistics;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.MetadataCapableConnector;
import com.syncflow.core.spi.ValidationResult;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public abstract class AbstractJdbcMetadataConnector implements MetadataCapableConnector {

    protected Connection jdbcConnection;

    protected abstract ConnectorType connectorType();
    protected abstract String jdbcUrl(ConnectionConfiguration config);
    protected abstract Properties jdbcProperties(ConnectionConfiguration config);

    @Override
    public ConnectorType type() {
        return connectorType();
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return ConnectorCapabilities.full();
    }

    @Override
    public void connect(ConnectorContext ctx) {
        disconnect();
        try {
            jdbcConnection = DriverManager.getConnection(
                    jdbcUrl(ctx.config()), jdbcProperties(ctx.config()));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (jdbcConnection != null) {
            try {
                jdbcConnection.close();
            } catch (SQLException ignored) {
            }
            jdbcConnection = null;
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return jdbcConnection != null && !jdbcConnection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        try (var c = DriverManager.getConnection(jdbcUrl(ctx.config()), jdbcProperties(ctx.config()))) {
            return ValidationResult.ok();
        } catch (SQLException e) {
            return ValidationResult.failed(List.of(e.getMessage()));
        }
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        ensureConnected(ctx);
        var schemas = new ArrayList<String>();
        try {
            var rs = jdbcConnection.getMetaData().getSchemas();
            while (rs.next())
                schemas.add(rs.getString("TABLE_SCHEM"));
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("Schema discovery failed", e);
        }
        return schemas;
    }

    @Override
    public List<TableMetadata> fetchTables(ConnectorContext ctx, String schema) {
        requireIdentifier(schema, "schema");
        ensureConnected(ctx);
        var list = new ArrayList<TableMetadata>();
        try {
            var rs = jdbcConnection.getMetaData().getTables(
                    null, schema, "%", new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"});
            while (rs.next()) {
                list.add(new TableMetadata(
                        rs.getString("TABLE_NAME"),
                        rs.getString("TABLE_TYPE"),
                        schema, getStringOrNull(rs, "REMARKS"),
                        TableStatistics.unknown(), List.of(), List.of(), null, List.of(), List.of()));
            }
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("Table fetch failed for " + schema, e);
        }
        return list;
    }

    @Override
    public List<ColumnMetadata> fetchColumns(ConnectorContext ctx, String schema, String table) {
        requireIdentifier(schema, "schema");
        requireIdentifier(table, "table");
        ensureConnected(ctx);
        var cols = new ArrayList<ColumnMetadata>();
        try {
            var rs = jdbcConnection.getMetaData().getColumns(null, schema, table, "%");
            var pkCols = loadPkNames(ctx, schema, table);
            var fkCols = loadFkColumnNames(ctx, schema, table);
            int pos = 1;
            while (rs.next()) {
                var name = rs.getString("COLUMN_NAME");
                var typeName = rs.getString("TYPE_NAME");
                var sqlType = rs.getInt("DATA_TYPE");
                var size = rs.getObject("COLUMN_SIZE", Integer.class);
                var digits = rs.getObject("DECIMAL_DIGITS", Integer.class);
                var nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                var def = rs.getString("COLUMN_DEF");
                var autoInc = "YES".equalsIgnoreCase(getStringOrNull(rs, "IS_AUTOINCREMENT"));
                var comment = getStringOrNull(rs, "REMARKS");
                cols.add(new ColumnMetadata(name, pos++,
                        new DataType(typeName, typeName, size, digits, nullable, def),
                        pkCols.contains(name), fkCols.contains(name), false, autoInc, comment));
            }
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("Column fetch failed for " + schema + "." + table, e);
        }
        return cols;
    }

    @Override
    public List<IndexMetadata> fetchIndexes(ConnectorContext ctx, String schema, String table) {
        requireIdentifier(schema, "schema");
        requireIdentifier(table, "table");
        ensureConnected(ctx);
        var map = new LinkedHashMap<String, IndexMetadata>();
        try {
            var rs = jdbcConnection.getMetaData().getIndexInfo(null, schema, table, false, false);
            while (rs.next()) {
                var name = rs.getString("INDEX_NAME");
                if (name == null)
                    continue;
                var colName = rs.getString("COLUMN_NAME");
                var nonUnique = rs.getBoolean("NON_UNIQUE");
                var type = rs.getShort("TYPE");
                map.computeIfAbsent(name,
                        n -> new IndexMetadata(n, new ArrayList<>(), !nonUnique, false, indexTypeName(type)));
                map.get(name).columnNames().add(colName);
            }
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("Index fetch failed for " + schema + "." + table, e);
        }
        return List.copyOf(map.values());
    }

    @Override
    public PrimaryKeyMetadata fetchPrimaryKey(ConnectorContext ctx, String schema, String table) {
        requireIdentifier(schema, "schema");
        requireIdentifier(table, "table");
        ensureConnected(ctx);
        var cols = new ArrayList<String>();
        String name = null;
        try {
            var rs = jdbcConnection.getMetaData().getPrimaryKeys(null, schema, table);
            while (rs.next()) {
                if (name == null)
                    name = rs.getString("PK_NAME");
                cols.add(rs.getString("COLUMN_NAME"));
            }
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("PK fetch failed", e);
        }
        return cols.isEmpty() ? null : new PrimaryKeyMetadata(name, cols);
    }

    @Override
    public List<ForeignKeyMetadata> fetchForeignKeys(ConnectorContext ctx, String schema, String table) {
        requireIdentifier(schema, "schema");
        requireIdentifier(table, "table");
        ensureConnected(ctx);
        var map = new LinkedHashMap<String, ForeignKeyMetadata.Builder>();
        try {
            var rs = jdbcConnection.getMetaData().getImportedKeys(null, schema, table);
            while (rs.next()) {
                var name = rs.getString("FK_NAME");
                if (name == null)
                    name = "fk_" + rs.getString("KEY_SEQ");
                var col = rs.getString("FKCOLUMN_NAME");
                var refCol = rs.getString("PKCOLUMN_NAME");
                var refSchema = rs.getString("PKTABLE_SCHEM");
                var refTable = rs.getString("PKTABLE_NAME");
                map.computeIfAbsent(name, n -> new ForeignKeyMetadata.Builder(n, refSchema, refTable));
                var b = map.get(name);
                b.columnNames().add(col);
                b.referencedColumns().add(refCol);
                b.deleteRule(ruleName(rs.getShort("DELETE_RULE")));
                b.updateRule(ruleName(rs.getShort("UPDATE_RULE")));
            }
            rs.close();
        } catch (SQLException e) {
            throw new RuntimeException("FK fetch failed for " + schema + "." + table, e);
        }
        return map.values().stream().map(ForeignKeyMetadata.Builder::build).toList();
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
        return isConnected() ? ConnectorHealth.up(0) : ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("connectorType", connectorType().name());
    }

    // ---- protected helpers ----

    protected void ensureConnected(ConnectorContext ctx) {
        if (!isConnected())
            connect(ctx);
    }

    protected Set<String> loadPkNames(ConnectorContext ctx, String schema, String table) {
        var pk = fetchPrimaryKey(ctx, schema, table);
        return pk == null ? Set.of() : Set.copyOf(pk.columnNames());
    }

    protected Set<String> loadFkColumnNames(ConnectorContext ctx, String schema, String table) {
        var set = new HashSet<String>();
        fetchForeignKeys(ctx, schema, table).forEach(fk -> set.addAll(fk.columnNames()));
        return set;
    }

    /**
     * Reject caller-supplied schema/table values that aren't plain SQL
     * identifiers. These reach JDBC metadata calls and connector queries, so a
     * value like `users; DROP TABLE x` must never be passed through.
     */
    protected void requireIdentifier(String value, String label) {
        if (value == null || value.isBlank()
                || !value.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException(
                    "Invalid " + label + " identifier: '" + value + "'");
        }
    }

    protected String getStringOrNull(ResultSet rs, String col) throws SQLException {
        try {
            var v = rs.getString(col);
            return rs.wasNull() ? null : v;
        } catch (SQLException e) {
            return null;
        }
    }

    // ---- private ----

    private String indexTypeName(short t) {
        return switch (t) {
            case DatabaseMetaData.tableIndexHashed -> "HASHED";
            case DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
            default -> "BTREE";
        };
    }

    private String ruleName(short r) {
        return switch (r) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            default -> "NO ACTION";
        };
    }
}
