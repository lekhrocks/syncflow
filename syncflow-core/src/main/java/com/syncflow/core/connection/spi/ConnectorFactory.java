package com.syncflow.core.connection.spi;

import com.syncflow.core.connection.ConnectionType;

import java.util.List;
import java.util.Optional;

public interface ConnectorFactory {

    Optional<ConnectionValidator> getValidator(ConnectionType type);

    List<ConnectionValidator> allValidators();
}
