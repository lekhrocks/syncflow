package com.syncflow.connector.metadata;

import com.syncflow.connector.snapshot.AbstractJdbcSnapshotConnector;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.metadata.TableStatistics;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.ConnectorContext;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class PostgresMetadataConnector extends AbstractJdbcSnapshotConnector {

    @Override
    protected ConnectorType connectorType() {
        return ConnectorType.POSTGRESQL;
    }

    @Override
    protected String jdbcUrl(ConnectionConfiguration config) {
        var url = new StringBuilder("jdbc:postgresql://");
        url.append(config.host()).append(':').append(config.port());
        url.append('/').append(config.database());
        if (!config.properties().isEmpty()) {
            url.append('?');
            config.properties().forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
            url.setLength(url.length() - 1);
        }
        return url.toString();
    }

    @Override
    protected Properties jdbcProperties(ConnectionConfiguration config) {
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", config.password());
        props.setProperty("connectTimeout", "10");
        return props;
    }

    @Override
    public List<TableMetadata> fetchTables(ConnectorContext ctx, String schema) {
        ensureConnected(ctx);
        var tables = new ArrayList<TableMetadata>();
        var sql = """
                SELECT c.relname AS table_name,
                       CASE c.relkind
                           WHEN 'r' THEN 'TABLE'
                           WHEN 'v' THEN 'VIEW'
                           WHEN 'm' THEN 'MATERIALIZED VIEW'
                           WHEN 'S' THEN 'SEQUENCE'
                           ELSE 'UNKNOWN'
                       END AS table_type,
                       pg_catalog.obj_description(c.oid, 'pg_class') AS comment,
                       c.reltuples::bigint AS row_estimate,
                       pg_catalog.pg_table_size(c.oid) AS total_bytes,
                       pg_catalog.pg_relation_size(c.oid) AS data_bytes,
                       pg_catalog.pg_indexes_size(c.oid) AS index_bytes
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ?
                  AND c.relkind IN ('r', 'v', 'm', 'S')
                  AND c.relispartition = false
                ORDER BY c.relname
                """;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                var name = rs.getString("table_name");
                var type = rs.getString("table_type");
                var comment = rs.getString("comment");
                var stats = new TableStatistics(
                        rs.getLong("row_estimate"),
                        rs.getLong("total_bytes"),
                        rs.getLong("data_bytes"),
                        rs.getLong("index_bytes"),
                        0, 0);
                tables.add(new TableMetadata(name, type, schema, comment,
                        stats, List.of(), List.of(), null, List.of(), List.of()));
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL table discovery failed", e);
        }
        return tables;
    }

    @Override
    public List<ConstraintMetadata> fetchConstraints(ConnectorContext ctx, String schema, String table) {
        ensureConnected(ctx);
        var constraints = new ArrayList<ConstraintMetadata>();
        var sql = """
                SELECT con.conname AS name,
                       CASE con.contype
                           WHEN 'c' THEN 'CHECK'
                           WHEN 'u' THEN 'UNIQUE'
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'f' THEN 'FOREIGN KEY'
                           WHEN 'x' THEN 'EXCLUDE'
                           ELSE 'OTHER'
                       END AS type,
                       pg_catalog.pg_get_constraintdef(con.oid) AS definition
                FROM pg_catalog.pg_constraint con
                JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relname = ?
                ORDER BY con.conname
                """;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                constraints.add(new ConstraintMetadata(
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("definition"),
                        List.of()));
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL constraint discovery failed", e);
        }
        return constraints;
    }

    @Override
    public TableStatistics fetchStatistics(ConnectorContext ctx, String schema, String table) {
        ensureConnected(ctx);
        var sql = """
                SELECT c.reltuples::bigint AS row_estimate,
                       pg_catalog.pg_table_size(c.oid) AS total_bytes,
                       pg_catalog.pg_relation_size(c.oid) AS data_bytes,
                       pg_catalog.pg_indexes_size(c.oid) AS index_bytes,
                       pg_catalog.pg_stat_get_live_tuples(c.oid) AS live,
                       pg_catalog.pg_stat_get_dead_tuples(c.oid) AS dead
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relname = ?
                """;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return new TableStatistics(
                        rs.getLong("row_estimate"),
                        rs.getLong("total_bytes"),
                        rs.getLong("data_bytes"),
                        rs.getLong("index_bytes"),
                        rs.getLong("live"),
                        rs.getLong("dead"));
            }
        } catch (SQLException ignored) {
        }
        return TableStatistics.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "16+", "vendor", "PostgreSQL");
    }
}
