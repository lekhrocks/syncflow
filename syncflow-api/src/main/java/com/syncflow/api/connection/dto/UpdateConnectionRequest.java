package com.syncflow.api.connection.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UpdateConnectionRequest(
        @NotBlank String name,
        @NotBlank String host,
        int port,
        @NotBlank String database,
        String username,
        String password,
        Map<String, String> options) {
}
