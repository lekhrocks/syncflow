package com.syncflow.core.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ConnectionConfiguration(
        @NotNull ConnectorType connectorType,
        @NotBlank String host,
        int port,
        @NotBlank String database,
        String username,
        String password,
        @NotNull Map<String, String> properties) {

    public ConnectionConfiguration {
        properties = Map.copyOf(properties == null ? Map.of() : properties);
    }
}
