package com.syncflow.api.controller;

import com.syncflow.api.agent.ai.domain.AgentContext;
import com.syncflow.api.agent.ai.domain.AgentResult;
import com.syncflow.api.agent.ai.domain.Conversation;
import com.syncflow.api.agent.ai.domain.ReasoningPlan;
import com.syncflow.api.agent.ai.knowledge.KnowledgeBase;
import com.syncflow.api.agent.ai.orchestrator.AgentOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class EnterpriseAiController {

    private final AgentOrchestrator orchestrator;
    private final KnowledgeBase knowledgeBase;

    public EnterpriseAiController(AgentOrchestrator orchestrator, KnowledgeBase knowledgeBase) {
        this.orchestrator = orchestrator;
        this.knowledgeBase = knowledgeBase;
    }

    @PostMapping("/chat")
    public ResponseEntity<Conversation> chat(@RequestBody Map<String, String> body) {
        var session = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        var userId = body.getOrDefault("userId", "anonymous");
        var tenantId = body.getOrDefault("tenantId", "default");
        var message = body.get("message");
        var conv = orchestrator.chat(session, userId, tenantId, message);
        return ResponseEntity.ok(conv);
    }

    @PostMapping("/plan")
    public ResponseEntity<ReasoningPlan> createPlan(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orchestrator.createPlan(body.get("goal")));
    }

    @PostMapping("/analyze")
    public ResponseEntity<List<AgentResult>> analyze(@RequestBody Map<String, String> body) {
        var plan = orchestrator.createPlan(body.get("goal"));
        var context = new AgentContext(
                body.get("workspaceId"), body.get("pipelineId"),
                body.get("connectionId"), Map.of());
        return ResponseEntity.ok(orchestrator.executePlan(plan, context));
    }

    @PostMapping("/document")
    public ResponseEntity<List<KnowledgeBase.Document>> document(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(knowledgeBase.search(body.get("query")));
    }

    @PostMapping("/review")
    public ResponseEntity<List<AgentResult>> review(@RequestBody Map<String, String> body) {
        var plan = orchestrator.createPlan("review " + body.getOrDefault("pipelineId", ""));
        var context = new AgentContext(null, body.get("pipelineId"), null, Map.of());
        return ResponseEntity.ok(orchestrator.executePlan(plan, context));
    }

    @PostMapping("/recommend")
    public ResponseEntity<List<AgentResult>> recommend(@RequestBody Map<String, String> body) {
        var plan = orchestrator.createPlan("optimize " + body.getOrDefault("pipelineId", ""));
        var context = new AgentContext(null, body.get("pipelineId"), null, Map.of());
        return ResponseEntity.ok(orchestrator.executePlan(plan, context));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Conversation>> history(@RequestParam String sessionId) {
        return ResponseEntity.ok(orchestrator.history(sessionId));
    }
}
