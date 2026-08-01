package com.syncflow.api.agent.ai.domain;

import java.util.List;

public record ReasoningPlan(
        String goal,
        List<Step> steps) {

    public record Step(
            int order,
            String action,
            String agentType,
            AgentCapability capability,
            String expectedOutcome) {
    }
}
