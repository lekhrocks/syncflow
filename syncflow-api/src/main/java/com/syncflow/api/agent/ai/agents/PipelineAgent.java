package com.syncflow.api.agent.ai.agents;

import com.syncflow.api.agent.ai.domain.AgentCapability;
import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.AgentTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PipelineAgent extends BaseAgent {

    public PipelineAgent() {
        super("PipelineAgent", AgentCapability.PIPELINE_DESIGN,
                "Designs and validates data synchronization pipelines");
    }

    @Override
    public AgentResult execute(AgentTask task, AgentContext context, List<AgentResult> previousResults) {
        var prompt = "Design a pipeline to synchronize data. Task: " + task.description()
                + ". Context includes connection " + context.connectionId();
        var analysis = callLlm("You are a pipeline design expert.", prompt);
        return new AgentResult(true, "Pipeline design generated", analysis,
                "POST /api/pipelines", true);
    }
}
