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
public class MySqlMetadataConnector extends AbstractJdbcSnapshotConnector {

    @Override
    protected ConnectorType connectorType() {
        return ConnectorType.MYSQL;
    }

    @Override
    protected String jdbcUrl(ConnectionConfiguration config) {
        var url = new StringBuilder("jdbc:mysql://");
        url.append(config.host()).append(':').append(config.port());
        url.append('/').append(config.database());
        url.append("?useInformationSchema=true");
        if (!config.properties().isEmpty()) {
            config.properties().forEach((k, v) -> url.append('&').append(k).append('=').append(v));
        }
        return url.toString();
    }

    @Override
    protected Properties jdbcProperties(ConnectionConfiguration config) {
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", config.password());
        props.setProperty("connectTimeout", "10000");
        return props;
    }

    @Override
    public List<TableMetadata> fetchTables(ConnectorContext ctx, String schema) {
        ensureConnected(ctx);
        var tables = new ArrayList<TableMetadata>();
        var sql = """
                SELECT TABLE_NAME, TABLE_TYPE, TABLE_COMMENT
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE IN ('BASE TABLE', 'VIEW')
                ORDER BY TABLE_NAME
                """;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                var name = rs.getString("TABLE_NAME");
                var type = "VIEW".equals(rs.getString("TABLE_TYPE")) ? "VIEW" : "TABLE";
                var comment = rs.getString("TABLE_COMMENT");
                tables.add(new TableMetadata(name, type, schema,
                        (comment != null && !comment.isEmpty()) ? comment : null,
                        TableStatistics.unknown(), List.of(), List.of(), null, List.of(), List.of()));
            }
        } catch (SQLException e) {
            throw new RuntimeException("MySQL table discovery failed", e);
        }
        return tables;
    }

    @Override
    public List<ConstraintMetadata> fetchConstraints(ConnectorContext ctx, String schema, String table) {
        ensureConnected(ctx);
        var constraints = new ArrayList<ConstraintMetadata>();
        var sql = """
                SELECT tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE,
                       cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                LEFT JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                    ON tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ?
                  AND tc.CONSTRAINT_TYPE IN ('UNIQUE', 'CHECK')
                ORDER BY tc.CONSTRAINT_NAME
                """;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                constraints.add(new ConstraintMetadata(
                        rs.getString("CONSTRAINT_NAME"),
                        rs.getString("CONSTRAINT_TYPE"),
                        rs.getString("CHECK_CLAUSE"),
                        List.of()));
            }
        } catch (SQLException e) {
            throw new RuntimeException("MySQL constraint discovery failed", e);
        }
        return constraints;
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "8.0+", "vendor", "MySQL");
    }
}
