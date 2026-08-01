package com.syncflow.core.registry;

import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.Connector;
import java.util.List;
import java.util.Optional;

public interface ConnectorRegistry {

    Connector register(Connector connector);

    void unregister(ConnectorType type);

    Optional<Connector> get(ConnectorType type);

    List<Connector> getAll();

    boolean isRegistered(ConnectorType type);
}
