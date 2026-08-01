package com.syncflow.core.governance;

import java.time.Instant;

public record DataLineageRecord(
        String id,
        String pipelineId,
        String sourceConnectionId,
        String sourceSchema,
        String sourceTable,
        String sourceColumns,
        String destinationConnectionId,
        String destinationSchema,
        String destinationTable,
        String destinationColumns,
        String transformationSummary,
        long rowsProcessed,
        Instant timestamp) {
}
