package com.syncflow.core.snapshot;

public record SnapshotCheckpoint(
        String pipelineId,
        String sourceTable,
        int chunkIndex,
        int lastBatchNumber,
        long rowsProcessed,
        String cursor) {

    /**
     * Whole-table (chunk 0) checkpoint — the pre-F15 shape, kept so existing
     * callers and tests constructing a checkpoint without a chunk compile.
     */
    public SnapshotCheckpoint(String pipelineId, String sourceTable,
            int lastBatchNumber, long rowsProcessed, String cursor) {
        this(pipelineId, sourceTable, 0, lastBatchNumber, rowsProcessed, cursor);
    }
}
