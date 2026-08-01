package com.syncflow.api.ai;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.api.ops.health.HealthAggregator;
import com.syncflow.api.ops.performance.PerformanceAnalyzer;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.sync.DeadLetterQueue;
import com.syncflow.api.sync.SyncOrchestrator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiCopilotService {

    private final ConnectionService connectionService;
    private final MetadataDiscoveryService metadataService;
    private final PipelineDesignerService pipelineService;
    private final SyncOrchestrator syncOrchestrator;
    private final DeadLetterQueue dlq;
    private final HealthAggregator health;
    private final PerformanceAnalyzer perf;
    private final ContextCollector contextCollector;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ConversationHistory conversation;

    public AiCopilotService(ConnectionService connectionService,
            MetadataDiscoveryService metadataService,
            PipelineDesignerService pipelineService,
            SyncOrchestrator syncOrchestrator,
            DeadLetterQueue dlq,
            HealthAggregator health,
            PerformanceAnalyzer perf,
            ContextCollector contextCollector,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            ConversationHistory conversation) {
        this.connectionService = connectionService;
        this.metadataService = metadataService;
        this.pipelineService = pipelineService;
        this.syncOrchestrator = syncOrchestrator;
        this.dlq = dlq;
        this.health = health;
        this.perf = perf;
        this.contextCollector = contextCollector;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.conversation = conversation;
    }

    public AiResponse chat(String sessionId, String message) {
        var context = contextCollector.collect();
        var prompt = promptBuilder.chat(context, message);
        conversation.add(sessionId, "user", message);
        var raw = llmClient.call(prompt, sessionId);
        var response = AiResponse.fromRaw(raw);
        conversation.add(sessionId, "assistant", response.message());
        return response;
    }

    public AiResponse generatePipeline(String sessionId, String description) {
        var context = contextCollector.collect();
        var prompt = promptBuilder.generatePipeline(context, description);
        var raw = llmClient.call(prompt, sessionId);
        return AiResponse.fromRaw(raw);
    }

    public AiResponse generateMapping(String sessionId, String sourceConnId,
            String sourceSchema, String sourceTable,
            String destConnId, String destSchema,
            String destTable) {
        var srcColumns = metadataService.discoverColumns(sourceConnId, sourceSchema, sourceTable);
        var destColumns = metadataService.discoverColumns(destConnId, destSchema, destTable);
        var prompt = promptBuilder.generateMapping(srcColumns, destColumns);
        var raw = llmClient.call(prompt, sessionId);
        return AiResponse.fromRaw(raw);
    }

    public AiResponse reviewPipeline(String sessionId, String pipelineId) {
        var pipeline = pipelineService.get(pipelineId);
        var context = contextCollector.collect();
        var prompt = promptBuilder.reviewPipeline(context, pipeline);
        var raw = llmClient.call(prompt, sessionId);
        return AiResponse.fromRaw(raw);
    }

    public AiResponse analyzePerformance(String sessionId, String pipelineId) {
        var stats = syncOrchestrator.statistics(pipelineId);
        var context = contextCollector.collect();
        var prompt = promptBuilder.analyzePerformance(context, stats);
        var raw = llmClient.call(prompt, sessionId);
        return AiResponse.fromRaw(raw);
    }

    public AiResponse rootCause(String sessionId, String pipelineId) {
        var dlqEvents = dlq.list(pipelineId);
        var context = contextCollector.collect();
        var prompt = promptBuilder.rootCause(context, dlqEvents);
        var raw = llmClient.call(prompt, sessionId);
        return AiResponse.fromRaw(raw);
    }

    public List<ConversationHistory.Entry> history(String sessionId) {
        return conversation.history(sessionId);
    }

    public record AiResponse(String message, boolean streaming, long tokensUsed, long latencyMs) {

        public static AiResponse fromRaw(String raw) {
            return new AiResponse(raw, false, 0, 0);
        }
    }
}
