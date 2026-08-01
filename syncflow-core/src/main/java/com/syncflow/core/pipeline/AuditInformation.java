package com.syncflow.core.pipeline;

import java.time.Instant;

public record AuditInformation(
        int version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy) {
}
