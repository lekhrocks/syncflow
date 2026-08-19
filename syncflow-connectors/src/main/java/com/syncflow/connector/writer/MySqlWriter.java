package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Component
public class MySqlWriter extends PooledJdbcBatchWriter {

    @Override
    protected String poolType() {
        return "mysql";
    }

    @Override
    protected String jdbcUrl(ConnectionConfiguration config) {
        return "jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database()
                + "?rewriteBatchedStatements=true&useSSL=false";
    }

    @Override
    protected Properties jdbcProperties(ConnectionConfiguration config) {
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", config.password());
        return props;
    }

    /**
     * MySQL UPSERT: {@code INSERT ... ON DUPLICATE KEY UPDATE ...}. The base
     * {@link JdbcBatchWriter#upsertSql} emits Postgres {@code ON CONFLICT}
     * syntax, which MySQL rejects; without this override the snapshot/CDC
     * upsert path would fail at write time for a MySQL destination.
     */
    @Override
    protected String upsertSql(String table, List<String> columns, List<String> keyColumns) {
        var cols = String.join(", ", columns);
        var params = "?" + ", ?".repeat(columns.size() - 1);
        var updateClause = columns.stream()
                .filter(c -> !keyColumns.contains(c))
                .map(c -> c + " = new." + c)
                .collect(Collectors.joining(", "));
        // Row-alias form: INSERT ... VALUES (...) AS new ON DUPLICATE KEY UPDATE
        // c = new.c — correct on MySQL 8.0.20+ (the old VALUES(col) function was
        // deprecated there). Falls to the key column itself when every INSERT
        // column is a key (nothing else to update).
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + params + ")"
                + " AS new ON DUPLICATE KEY UPDATE "
                + (updateClause.isEmpty() ? columns.getFirst() + " = new." + columns.getFirst()
                        : updateClause);
    }
}
