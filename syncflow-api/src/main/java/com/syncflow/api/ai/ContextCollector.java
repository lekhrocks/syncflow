package com.syncflow.api.ai;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.pipeline.PipelineDesign;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextCollector {

    private final ConnectionService connectionService;
    private final PipelineDesignerService pipelineService;
    private final SyncOrchestrator syncOrchestrator;

    public ContextCollector(ConnectionService connectionService,
            PipelineDesignerService pipelineService,
            SyncOrchestrator syncOrchestrator) {
        this.connectionService = connectionService;
        this.pipelineService = pipelineService;
        this.syncOrchestrator = syncOrchestrator;
    }

    public SyncFlowContext collect() {
        var connections = connectionService.list();
        var pipelines = pipelineService.list();
        var syncJobs = syncOrchestrator.list();

        return new SyncFlowContext(
                sanitizeConnections(connections),
                pipelines,
                syncJobs.stream()
                        .map(j -> new JobSummary(j.getPipelineId(), j.getState().name(),
                                j.getStatistics().processedEvents(), j.getStatistics().failedEvents()))
                        .toList());
    }

    private List<ConnectionSummary> sanitizeConnections(List<Connection> connections) {
        return connections.stream()
                .map(c -> new ConnectionSummary(
                        c.getId().value(), c.getName(),
                        c.getProperties().type().name(),
                        c.getProperties().host(), c.getProperties().port(),
                        c.getProperties().database(), c.getStatus().name()))
                .toList();
    }

    public record SyncFlowContext(
            List<ConnectionSummary> connections,
            List<PipelineDesign> pipelines,
            List<JobSummary> syncJobs) {
    }

    public record ConnectionSummary(
            String id, String name, String type, String host,
            int port, String database, String status) {
    }

    public record JobSummary(String pipelineId, String state, long processed, long failed) {
    }
}
