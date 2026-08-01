package com.syncflow.api.connection.dto;

import com.syncflow.core.connection.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TestConnectionRequest(
        @NotNull ConnectionType connectionType,
        @NotBlank String host,
        int port,
        @NotBlank String database,
        String username,
        String password,
        Map<String, String> options) {
}
