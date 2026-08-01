package com.syncflow.connector.validator;

import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.DetailedValidationResult;
import com.syncflow.core.connection.spi.ConnectionValidator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public abstract class AbstractConnectorValidator implements ConnectionValidator {

    private final ConnectionType supportedType;

    protected AbstractConnectorValidator(ConnectionType supportedType) {
        this.supportedType = supportedType;
    }

    @Override
    public boolean supports(ConnectionType type) {
        return supportedType == type;
    }

    @Override
    public DetailedValidationResult validate(ConnectionProperties props, Credentials credentials) {
        var errors = new ArrayList<String>();

        if (!validateHost(props.host()))
            errors.add("Invalid or unreachable host: " + props.host());
        if (!validatePort(props.port()))
            errors.add("Invalid port: " + props.port());
        if (!validateDatabase(props.database()))
            errors.add("Database name validation failed");

        if (!errors.isEmpty()) {
            return DetailedValidationResult.failed(errors);
        }

        var start = System.currentTimeMillis();
        try (var connection = tryConnect(props, credentials)) {
            var meta = connection.getMetaData();
            var latency = System.currentTimeMillis() - start;
            return DetailedValidationResult.ok(
                    meta.getDatabaseProductVersion(),
                    meta.getDriverName(),
                    latency);
        } catch (Exception e) {
            return DetailedValidationResult.failed(List.of(e.getMessage()));
        }
    }

    protected boolean validateHost(String host) {
        return host != null && !host.isBlank();
    }

    protected boolean validatePort(int port) {
        return port > 0 && port <= 65535;
    }

    protected boolean validateDatabase(String database) {
        return database != null && !database.isBlank();
    }

    protected abstract String jdbcUrl(ConnectionProperties props);

    protected Properties jdbcProperties(Credentials credentials) {
        var props = new Properties();
        props.setProperty("user", credentials.username());
        props.setProperty("password", credentials.password());
        props.setProperty("connectTimeout", "10");
        props.setProperty("loginTimeout", "10");
        return props;
    }

    private Connection tryConnect(ConnectionProperties props, Credentials credentials) throws Exception {
        return DriverManager.getConnection(jdbcUrl(props), jdbcProperties(credentials));
    }
}
