package com.syncflow.api.ops.alert;

import java.time.Instant;

public record AlertEvent(
        String id,
        String name,
        String message,
        AlertSeverity severity,
        String source,
        String pipelineId,
        String connectionId,
        Instant timestamp,
        boolean acknowledged) {
}
