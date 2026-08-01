package com.syncflow.core.spi;

import com.syncflow.core.model.ConnectionConfiguration;
import java.util.Map;

public record ConnectorContext(
        ConnectionConfiguration config,
        Map<String, String> runtimeProperties) {

    public ConnectorContext {
        runtimeProperties = Map.copyOf(runtimeProperties == null ? Map.of() : runtimeProperties);
    }
}
