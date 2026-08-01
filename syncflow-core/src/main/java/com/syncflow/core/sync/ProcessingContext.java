package com.syncflow.core.sync;

public record ProcessingContext(
        String pipelineId,
        String syncJobId,
        String sourceTable,
        String destinationTable,
        int batchNumber) {
}
