package com.syncflow.api.pipeline.dto;

import com.syncflow.core.pipeline.SyncMode;
import com.syncflow.core.pipeline.mapping.TableMapping;
import java.util.List;
import java.util.Map;

public record UpdatePipelineDesignRequest(
        String name,
        String sourceConnectionId,
        String sourceSchema,
        String sourceTable,
        String destConnectionId,
        String destSchema,
        String destTable,
        List<TableMapping> tableMappings,
        SyncMode syncMode,
        Integer batchSize,
        Map<String, String> settings) {
}
