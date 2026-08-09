package com.syncflow.core.model;

import java.time.Instant;

public record PipelineEvent(
        String pipelineId,
        PipelineStatus previousStatus,
        PipelineStatus newStatus,
        String reason,
        Instant timestamp) {
}
