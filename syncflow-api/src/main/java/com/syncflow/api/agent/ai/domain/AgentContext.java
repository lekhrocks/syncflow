package com.syncflow.api.agent.ai.domain;

import java.util.Map;

public record AgentContext(
        String workspaceId,
        String pipelineId,
        String connectionId,
        Map<String, String> metadata) {
}
