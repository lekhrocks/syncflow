package com.syncflow.core.snapshot;

public record BatchInformation(
        int batchNumber,
        int batchSize,
        String sourceTable,
        String cursor,
        ChunkRange chunkRange) {

    /**
     * Sequential (whole-table) page request — no chunk range. Kept for the
     * non-parallel path and callers that never chunk.
     */
    public BatchInformation(int batchNumber, int batchSize, String sourceTable, String cursor) {
        this(batchNumber, batchSize, sourceTable, cursor, null);
    }
}
