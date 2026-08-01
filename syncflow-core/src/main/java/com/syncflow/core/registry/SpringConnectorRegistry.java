package com.syncflow.core.registry;

import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.Connector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpringConnectorRegistry implements ConnectorRegistry {

    private final Map<ConnectorType, Connector> registry = new ConcurrentHashMap<>();

    public SpringConnectorRegistry(List<Connector> connectors) {
        connectors.forEach(this::register);
    }

    @Override
    public Connector register(Connector connector) {
        registry.put(connector.type(), connector);
        return connector;
    }

    @Override
    public void unregister(ConnectorType type) {
        var connector = registry.remove(type);
        if (connector != null)
            connector.disconnect();
    }

    @Override
    public Optional<Connector> get(ConnectorType type) {
        return Optional.ofNullable(registry.get(type));
    }

    @Override
    public List<Connector> getAll() {
        return List.copyOf(registry.values());
    }

    @Override
    public boolean isRegistered(ConnectorType type) {
        return registry.containsKey(type);
    }
}
