package com.syncflow.api.ops.performance;

import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.sync.SyncOrchestrator;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

@Component
public class PerformanceAnalyzer {

    private final PipelineDesignerService pipelineService;
    private final SyncOrchestrator syncOrchestrator;

    public PerformanceAnalyzer(PipelineDesignerService pipelineService,
            SyncOrchestrator syncOrchestrator) {
        this.pipelineService = pipelineService;
        this.syncOrchestrator = syncOrchestrator;
    }

    public Map<String, Object> analyze() {
        var pipelines = pipelineService.list();
        var syncJobs = syncOrchestrator.list();

        var slowest = pipelines.stream()
                .max(Comparator.comparing(p -> p.tableMappings().size()))
                .map(p -> p.name().value())
                .orElse("none");

        var avg = syncJobs.stream()
                .mapToLong(j -> j.getStatistics().processedEvents())
                .average().orElse(0);

        var totalEvents = syncJobs.stream()
                .mapToLong(j -> j.getStatistics().totalEvents()).sum();

        return Map.of(
                "totalPipelines", pipelines.size(),
                "activeSyncs", syncJobs.stream().filter(j -> j.getState().name().equals("RUNNING")).count(),
                "pipelineWithMostMappings", slowest,
                "avgEventsProcessed", Math.round(avg),
                "totalEvents", totalEvents);
    }
}
