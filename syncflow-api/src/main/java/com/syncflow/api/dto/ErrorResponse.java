package com.syncflow.api.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        int status,
        Instant timestamp,
        List<ValidationError> errors) {

    public record ValidationError(String field, String message) {
    }
}
