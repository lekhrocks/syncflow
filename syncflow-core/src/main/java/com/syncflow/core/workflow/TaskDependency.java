package com.syncflow.core.workflow;

import java.util.List;

public record TaskDependency(
        String taskId,
        List<String> dependsOn) {
}
