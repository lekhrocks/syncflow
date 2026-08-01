package com.syncflow.core.snapshot;

public record SnapshotProgress(
        int currentBatch,
        int totalBatches,
        long rowsProcessed,
        long estimatedTotalRows,
        double percentComplete,
        long elapsedMs) {

    public static SnapshotProgress starting(long estimatedTotalRows) {
        return new SnapshotProgress(0, 0, 0, estimatedTotalRows, 0, 0);
    }
}
