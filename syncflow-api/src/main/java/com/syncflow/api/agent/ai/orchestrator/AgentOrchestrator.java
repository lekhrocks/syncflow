package com.syncflow.api.agent.ai.orchestrator;

import com.syncflow.api.agent.ai.agents.BaseAgent;
import com.syncflow.api.agent.ai.domain.AgentCapability;
import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.AgentTask;
import com.syncflow.api.agent.ai.domain.Conversation;
import com.syncflow.api.agent.ai.domain.ReasoningPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentOrchestrator {

    private final Map<AgentCapability, BaseAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, List<Conversation>> conversations = new ConcurrentHashMap<>();

    public AgentOrchestrator(List<BaseAgent> agentBeans) {
        agentBeans.forEach(a -> agents.put(a.getCapability(), a));
    }

    public ReasoningPlan createPlan(String goal) {
        var steps = new ArrayList<ReasoningPlan.Step>();
        int order = 1;

        if (goal.toLowerCase().contains("pipeline") || goal.toLowerCase().contains("sync")) {
            steps.add(new ReasoningPlan.Step(order++, "Analyze pipeline requirements",
                    "PipelineAgent", AgentCapability.PIPELINE_DESIGN, "Pipeline design"));
            steps.add(new ReasoningPlan.Step(order++, "Determine connector types",
                    "ConnectorAgent", AgentCapability.CONNECTOR_SELECTION, "Connector selection"));
            steps.add(new ReasoningPlan.Step(order++, "Generate schema mappings",
                    "SchemaAgent", AgentCapability.SCHEMA_MAPPING, "Schema mapping"));
        }
        if (goal.toLowerCase().contains("perform") || goal.toLowerCase().contains("slow")
                || goal.toLowerCase().contains("latency")) {
            steps.add(new ReasoningPlan.Step(order++, "Analyze performance metrics",
                    "PerformanceAgent", AgentCapability.PERFORMANCE_OPTIMIZATION, "Performance analysis"));
        }
        if (goal.toLowerCase().contains("fail") || goal.toLowerCase().contains("error")
                || goal.toLowerCase().contains("root")) {
            steps.add(new ReasoningPlan.Step(order++, "Identify root cause",
                    "RootCauseAgent", AgentCapability.ROOT_CAUSE_ANALYSIS, "Root cause"));
        }
        if (goal.toLowerCase().contains("doc") || goal.toLowerCase().contains("explain")) {
            steps.add(new ReasoningPlan.Step(order++, "Generate documentation",
                    "DocumentationAgent", AgentCapability.DOCUMENTATION, "Documentation"));
        }
        if (goal.toLowerCase().contains("security") || goal.toLowerCase().contains("vulnerability")) {
            steps.add(new ReasoningPlan.Step(order++, "Security review",
                    "SecurityAdvisorAgent", AgentCapability.SECURITY_ADVISOR, "Security review"));
        }

        return new ReasoningPlan(goal, steps);
    }

    public List<AgentResult> executePlan(ReasoningPlan plan, AgentContext context) {
        var results = new ArrayList<AgentResult>();
        for (var step : plan.steps()) {
            var agent = agents.get(step.capability());
            if (agent == null)
                continue;
            var task = new AgentTask(UUID.randomUUID().toString(),
                    step.action(), step.capability(), Map.of("context", context.toString()), List.of());
            var result = agent.execute(task, context, results);
            results.add(result);
        }
        return results;
    }

    public Conversation chat(String conversationId, String userId, String tenantId, String message) {
        var history = conversations.computeIfAbsent(conversationId, k -> new ArrayList<>());
        var existing = history.isEmpty() ? null : history.getLast();
        var messages = new ArrayList<Conversation.Message>();

        if (existing != null)
            messages.addAll(existing.messages());
        messages.add(new Conversation.Message("user", message, java.time.Instant.now(), null));

        var plan = createPlan(message);
        var context = new AgentContext(null, null, null, Map.of());
        var results = executePlan(plan, context);

        var summary = new StringBuilder();
        for (var r : results) {
            summary.append(r.summary()).append(". ").append(r.details()).append("\n");
        }
        messages.add(new Conversation.Message("assistant", summary.toString(),
                java.time.Instant.now(), "Orchestrator"));

        var conv = new Conversation(conversationId, userId, tenantId, messages,
                existing != null ? existing.createdAt() : java.time.Instant.now(),
                java.time.Instant.now());
        history.add(conv);
        return conv;
    }

    public List<Conversation> history(String conversationId) {
        return conversations.getOrDefault(conversationId, List.of());
    }
}
