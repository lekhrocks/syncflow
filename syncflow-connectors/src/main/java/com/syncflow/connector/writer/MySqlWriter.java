package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class MySqlWriter extends JdbcBatchWriter {

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
}
