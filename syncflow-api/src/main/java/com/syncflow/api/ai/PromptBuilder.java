package com.syncflow.api.ai;

import com.syncflow.api.ai.ContextCollector.SyncFlowContext;
import com.syncflow.core.metadata.MetadataResponse;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.sync.SyncStatistics;
import com.syncflow.core.sync.dlq.DeadLetterEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are SyncFlow AI Copilot — an enterprise data synchronization platform assistant.

            CAPABILITIES:
            - Generate pipeline definitions from natural language descriptions
            - Suggest column mappings with data type conversions
            - Recommend transformations (rename, concat, case changes, defaults)
            - Review pipelines for issues and optimization opportunities
            - Analyze performance metrics and suggest improvements
            - Diagnose root causes of failures from error logs and DLQ events

            RULES:
            - NEVER suggest executing pipelines directly
            - NEVER expose passwords, connection strings, or secrets
            - NEVER invent database schemas — only work with existing metadata
            - ALWAYS explain your reasoning
            - ALWAYS note risks and assumptions
            - Use the existing SyncFlow domain model for all suggestions
            """;

    private static final String PIPELINE_GENERATION_TEMPLATE = """
            Generate a SyncFlow pipeline definition for: {description}

            Available connections: {connections}
            Available tables/schemas: {schemas}

            Return a JSON object with:
            - name: pipeline name
            - sourceConnectionId: existing connection id
            - sourceSchema: schema name
            - sourceTable: table or collection name
            - destConnectionId: existing connection id
            - destSchema: destination schema
            - destTable: destination table/collection
            - columnMappings: array of {source, destination, transformations}
            - filters: optional filter conditions
            """;

    private static final String MAPPING_TEMPLATE = """
            Generate column mappings between source and destination schemas.

            Source columns: {sourceColumns}
            Destination columns: {destinationColumns}

            For each source column, suggest:
            - destination column name (with type conversion if needed)
            - transformation rules (rename, case change, default, etc.)
            - primary key mapping if applicable
            """;

    private static final String REVIEW_TEMPLATE = """
            Review this SyncFlow pipeline: {pipeline}

            Platform context: {context}

            Analyze for:
            - Missing or incorrect mappings
            - Invalid transformations
            - Performance risks (large tables, missing indexes)
            - Unsupported data type conversions
            - Duplicate or conflicting field mappings
            - Missing primary keys
            """;

    public String chat(SyncFlowContext ctx, String message) {
        return SYSTEM_PROMPT + "\n\nCurrent platform state:\n" + formatContext(ctx)
                + "\n\nUser question: " + message;
    }

    public String generatePipeline(SyncFlowContext ctx, String description) {
        return SYSTEM_PROMPT + "\n\n" + PIPELINE_GENERATION_TEMPLATE
                .replace("{description}", description)
                .replace("{connections}", formatConnections(ctx))
                .replace("{schemas}", "Use metadata API to discover schemas");
    }

    public String generateMapping(MetadataResponse<?> src, MetadataResponse<?> dest) {
        return SYSTEM_PROMPT + "\n\n" + MAPPING_TEMPLATE
                .replace("{sourceColumns}", src.data().toString())
                .replace("{destinationColumns}", dest.data().toString());
    }

    public String reviewPipeline(SyncFlowContext ctx, PipelineDesign pipeline) {
        return SYSTEM_PROMPT + "\n\n" + REVIEW_TEMPLATE
                .replace("{pipeline}", pipeline.name().value())
                .replace("{context}", formatContext(ctx));
    }

    public String analyzePerformance(SyncFlowContext ctx, SyncStatistics stats) {
        return SYSTEM_PROMPT + "\n\nAnalyze these synchronization statistics and suggest performance improvements:\n"
                + "Processed: " + stats.processedEvents() + "\n"
                + "Failed: " + stats.failedEvents() + "\n"
                + "Retries: " + stats.retries() + "\n"
                + "DLQ count: " + stats.dlqCount() + "\n"
                + "Avg latency: " + stats.avgLatencyMs() + "ms\n\n"
                + "Platform context:\n" + formatContext(ctx);
    }

    public String rootCause(SyncFlowContext ctx, List<DeadLetterEvent> dlqEvents) {
        return SYSTEM_PROMPT + "\n\nAnalyze these failures and identify root causes:\n"
                + dlqEvents.stream()
                        .map(e -> "- Event: " + e.originalEvent().operation()
                                + " Reason: " + e.reason().message()
                                + " Retries: " + e.retryCount())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("No DLQ events")
                + "\n\nPlatform context:\n" + formatContext(ctx);
    }

    private String formatContext(SyncFlowContext ctx) {
        var sb = new StringBuilder();
        sb.append("Connections (").append(ctx.connections().size()).append("):\n");
        ctx.connections().forEach(c -> sb.append("  - ").append(c.name()).append(" (").append(c.type()).append(")\n"));
        sb.append("Pipelines (").append(ctx.pipelines().size()).append("):\n");
        ctx.pipelines().forEach(
                p -> sb.append("  - ").append(p.name().value()).append(" [").append(p.status()).append("]\n"));
        sb.append("Sync Jobs (").append(ctx.syncJobs().size()).append("):\n");
        ctx.syncJobs()
                .forEach(j -> sb.append("  - ").append(j.pipelineId()).append(": ").append(j.state()).append("\n"));
        return sb.toString();
    }

    private String formatConnections(SyncFlowContext ctx) {
        var sb = new StringBuilder();
        ctx.connections().forEach(c -> sb.append("  - ").append(c.id()).append(": ")
                .append(c.name()).append(" (").append(c.type()).append(") ")
                .append(c.host()).append(":").append(c.port()).append("/").append(c.database()).append("\n"));
        return sb.toString();
    }
}
