package com.syncflow.api.agent.ai.tools;

import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.sync.DeadLetterQueue;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final MetadataDiscoveryService metadataService;
    private final PipelineDesignerService pipelineService;
    private final DeadLetterQueue dlq;

    public ToolRegistry(MetadataDiscoveryService metadataService,
            PipelineDesignerService pipelineService,
            DeadLetterQueue dlq) {
        this.metadataService = metadataService;
        this.pipelineService = pipelineService;
        this.dlq = dlq;
    }

    public String searchMetadata(String connectionId, String schema, String table) {
        try {
            var columns = metadataService.discoverColumns(connectionId, schema, table);
            if (columns.error() != null)
                return "Error: " + columns.error();
            return "Found " + columns.totalCount() + " columns: " +
                    columns.data().stream().map(c -> c.name() + " (" + c.dataType().jdbcType() + ")").toList();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String inspectPipeline(String pipelineId) {
        try {
            var pipeline = pipelineService.get(pipelineId);
            return "Pipeline: " + pipeline.name().value()
                    + " (status: " + pipeline.status()
                    + ", mappings: " + pipeline.tableMappings().size() + ")";
        } catch (Exception e) {
            return "Pipeline not found: " + pipelineId;
        }
    }

    public String listConnectors() {
        return "Available connectors: POSTGRESQL, MYSQL, MONGODB, REDIS";
    }

    public String queryDlq(String pipelineId) {
        var events = dlq.list(pipelineId);
        return "DLQ has " + events.size() + " events for pipeline " + pipelineId;
    }
}
