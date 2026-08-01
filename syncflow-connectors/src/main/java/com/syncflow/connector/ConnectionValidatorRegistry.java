package com.syncflow.connector;

import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.spi.ConnectionValidator;
import com.syncflow.core.connection.spi.ConnectorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionValidatorRegistry implements ConnectorFactory {

    private final Map<ConnectionType, ConnectionValidator> validators = new ConcurrentHashMap<>();

    public ConnectionValidatorRegistry(List<ConnectionValidator> validatorBeans) {
        validatorBeans.forEach(v -> validators.put(deducedType(v), v));
    }

    @Override
    public Optional<ConnectionValidator> getValidator(ConnectionType type) {
        return Optional.ofNullable(validators.get(type));
    }

    @Override
    public List<ConnectionValidator> allValidators() {
        return List.copyOf(validators.values());
    }

    private static ConnectionType deducedType(ConnectionValidator v) {
        // Try each known type; the first one the validator supports wins.
        for (var t : ConnectionType.values()) {
            if (v.supports(t))
                return t;
        }
        throw new IllegalArgumentException("No ConnectionType for " + v.getClass().getSimpleName());
    }
}
