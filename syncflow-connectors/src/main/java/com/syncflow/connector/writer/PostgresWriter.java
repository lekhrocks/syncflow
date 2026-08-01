package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class PostgresWriter extends JdbcBatchWriter {

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
}
