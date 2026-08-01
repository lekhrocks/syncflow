package com.syncflow.core.spi.writer;

import com.syncflow.core.model.ConnectorType;
import java.util.Optional;

public interface WriterRegistry {

    void register(ConnectorType type, DestinationWriter writer);

    Optional<DestinationWriter> get(ConnectorType type);
}
