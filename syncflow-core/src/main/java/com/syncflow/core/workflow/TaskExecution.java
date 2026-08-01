package com.syncflow.core.workflow;

import java.time.Instant;

public record TaskExecution(
        String executionId,
        String taskId,
        TaskStatus status,
        String workerId,
        String error,
        int attempt,
        Instant startedAt,
        Instant completedAt) {
}
