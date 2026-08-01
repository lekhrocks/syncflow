package com.syncflow.api.agent.ai.domain;

import java.util.List;
import java.util.Map;

public record AgentTask(
        String taskId,
        String description,
        AgentCapability requiredCapability,
        Map<String, String> input,
        List<String> dependsOn) {
}
