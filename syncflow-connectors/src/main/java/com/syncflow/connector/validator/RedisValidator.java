package com.syncflow.connector.validator;

import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.DetailedValidationResult;
import com.syncflow.core.connection.spi.ConnectionValidator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisValidator implements ConnectionValidator {

    @Override
    public boolean supports(ConnectionType type) {
        return type == ConnectionType.REDIS;
    }

    @Override
    public DetailedValidationResult validate(ConnectionProperties props, Credentials credentials) {
        // ponytail: Redis validation via Jedis.
        // Uses Jedis directly when added as a dependency.
        // For now validates host/port format without a live connection.
        if (props.host() == null || props.host().isBlank()) {
            return DetailedValidationResult.failed(List.of("Host is required"));
        }
        if (props.port() <= 0 || props.port() > 65535) {
            return DetailedValidationResult.failed(List.of("Invalid port: " + props.port()));
        }
        return DetailedValidationResult.ok("Redis", "Jedis Driver", 0);
    }
}
