package com.syncflow.api.agent.ai.domain;

public record AgentResult(
        boolean success,
        String summary,
        String details,
        String suggestedCommand,
        boolean requiresApproval) {
}
