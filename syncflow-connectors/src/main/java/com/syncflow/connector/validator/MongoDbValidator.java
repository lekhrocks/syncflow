package com.syncflow.connector.validator;

import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.DetailedValidationResult;
import com.syncflow.core.connection.spi.ConnectionValidator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MongoDbValidator implements ConnectionValidator {

    @Override
    public boolean supports(ConnectionType type) {
        return type == ConnectionType.MONGODB;
    }

    @Override
    public DetailedValidationResult validate(ConnectionProperties props, Credentials credentials) {
        // Build a MongoDB URI. When username is blank (no-auth container), omit credentials.
        var hasAuth = credentials != null && !credentials.username().isBlank();
        String uri;
        if (hasAuth) {
            uri = "mongodb://" + credentials.username() + ":" + credentials.password()
                    + "@" + props.host() + ":" + props.port() + "/" + props.database();
        } else {
            uri = "mongodb://" + props.host() + ":" + props.port() + "/" + props.database();
        }
        try {
            var start = System.currentTimeMillis();
            var client = com.mongodb.client.MongoClients.create(uri);
            client.listDatabaseNames().first();
            client.close();
            var latency = System.currentTimeMillis() - start;
            return DetailedValidationResult.ok("MongoDB", "MongoDB Driver", latency);
        } catch (Exception e) {
            return DetailedValidationResult.failed(List.of(e.getMessage()));
        }
    }
}
