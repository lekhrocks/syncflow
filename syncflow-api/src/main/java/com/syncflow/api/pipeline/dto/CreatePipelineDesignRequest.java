package com.syncflow.api.pipeline.dto;

import com.syncflow.core.pipeline.SyncMode;
import com.syncflow.core.pipeline.mapping.TableMapping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record CreatePipelineDesignRequest(
        @NotBlank String name,
        @NotBlank String sourceConnectionId,
        @NotBlank String sourceSchema,
        @NotBlank String sourceTable,
        @NotBlank String destConnectionId,
        @NotBlank String destSchema,
        @NotBlank String destTable,
        String destWriteMode,
        @Valid List<TableMapping> tableMappings,
        SyncMode syncMode,
        Integer batchSize,
        Map<String, String> settings) {
}
