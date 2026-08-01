package com.syncflow.api.ops.audit;

import java.time.Instant;

public record AuditEvent(
        String id,
        String action,
        String entityType,
        String entityId,
        String details,
        String correlationId,
        String userId,
        boolean success,
        Instant timestamp) {
}
