package com.syncflow.api.agent.ai.agents;

import com.syncflow.api.agent.ai.domain.AgentCapability;
import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.AgentTask;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConnectorAgent extends BaseAgent {

    public ConnectorAgent() {
        super("ConnectorAgent", AgentCapability.CONNECTOR_SELECTION,
                "Recommends connectors based on source/destination databases");
    }

    @Override
    public AgentResult execute(AgentTask task, AgentContext context, List<AgentResult> previousResults) {
        return new AgentResult(true, "Connector selected",
                "Recommended connector type for the given databases.",
                null, false);
    }
}
