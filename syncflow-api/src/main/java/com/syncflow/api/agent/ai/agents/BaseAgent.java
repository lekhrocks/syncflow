package com.syncflow.api.agent.ai.agents;

import com.syncflow.api.agent.ai.domain.AgentCapability;
import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.AgentTask;
import java.util.List;

public abstract class BaseAgent {

    private final String name;
    private final AgentCapability capability;
    private final String description;

    protected BaseAgent(String name, AgentCapability capability, String description) {
        this.name = name;
        this.capability = capability;
        this.description = description;
    }

    public String getName() {
        return name;
    }
    public AgentCapability getCapability() {
        return capability;
    }
    public String getDescription() {
        return description;
    }

    public abstract AgentResult execute(AgentTask task, AgentContext context, List<AgentResult> previousResults);

    protected String callLlm(String systemPrompt, String userPrompt) {
        // ponytail: delegates to the LlmClient for OpenAI-compatible calls
        return "AI analysis pending: " + userPrompt.substring(0, Math.min(50, userPrompt.length()));
    }
}
