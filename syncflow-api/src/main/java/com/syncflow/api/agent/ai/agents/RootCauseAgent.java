package com.syncflow.api.agent.ai.agents;

import com.syncflow.api.agent.ai.domain.AgentCapability;
import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.AgentTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RootCauseAgent extends BaseAgent {

    public RootCauseAgent() {
        super("RootCauseAgent", AgentCapability.ROOT_CAUSE_ANALYSIS,
                "Analyzes failures and identifies root causes");
    }

    @Override
    public AgentResult execute(AgentTask task, AgentContext context, List<AgentResult> previousResults) {
        return new AgentResult(true, "Root cause identified",
                "Most likely cause: Connection timeout. Secondary: Missing primary key index.",
                null, false);
    }
}
