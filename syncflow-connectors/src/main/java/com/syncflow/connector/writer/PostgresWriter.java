package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Component
public class PostgresWriter extends PooledJdbcBatchWriter {

    @Override
    protected String poolType() {
        return "postgresql";
    }

    @Override
    protected String jdbcUrl(ConnectionConfiguration config) {
        return "jdbc:postgresql://" + config.host() + ":" + config.port() + "/" + config.database();
    }

    @Override
    protected Properties jdbcProperties(ConnectionConfiguration config) {
        var props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", config.password());
        props.setProperty("reWriteBatchedInserts", "true");
        return props;
    }

    /**
     * Postgres-specific UPSERT:
     * {@code INSERT ... ON CONFLICT (key) DO UPDATE SET ...}.
     * Conflict target uses the first key column for single-PK case; composite
     * keys work the same way.
     */
    @Override
    protected String upsertSql(String table, List<String> columns, List<String> keyColumns) {
        var cols = String.join(", ", columns);
        var params = "?" + ", ?".repeat(columns.size() - 1);
        var updateClause = columns.stream()
                .filter(c -> !keyColumns.contains(c))
                .map(c -> c + " = EXCLUDED." + c)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + params + ")"
                + " ON CONFLICT (" + String.join(", ", keyColumns) + ")"
                + " DO UPDATE SET "
                + (updateClause.isEmpty() ? columns.getFirst() + " = EXCLUDED." + columns.getFirst() : updateClause);
    }
}
