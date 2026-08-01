package com.syncflow.core.snapshot;

public record SnapshotCheckpoint(
        String pipelineId,
        String sourceTable,
        int lastBatchNumber,
        long rowsProcessed,
        String cursor) {
}
