package com.syncflow.api.agent.ai.agents;

import com.syncflow.api.agent.ai.domain.AgentCapability;
import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.AgentTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentationAgent extends BaseAgent {

    public DocumentationAgent() {
        super("DocumentationAgent", AgentCapability.DOCUMENTATION,
                "Generates documentation for pipelines, connectors, and operations");
    }

    @Override
    public AgentResult execute(AgentTask task, AgentContext context, List<AgentResult> previousResults) {
        return new AgentResult(true, "Documentation generated",
                "# Pipeline Documentation\n\nThis pipeline synchronizes data...",
                null, false);
    }
}
