package com.syncflow.core.workflow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record WorkflowTask(
        String taskId,
        String name,
        TaskType type,
        int retryCount,
        int maxRetries,
        Duration timeout,
        List<String> dependsOn,
        boolean isCompensation,
        Map<String, String> input) {
}
