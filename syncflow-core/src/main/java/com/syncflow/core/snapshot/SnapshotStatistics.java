package com.syncflow.core.snapshot;

import java.time.Instant;

public record SnapshotStatistics(
        long totalRows,
        long rowsProcessed,
        long batchesCompleted,
        long totalBatches,
        long bytesRead,
        long bytesWritten,
        Instant startedAt,
        Instant completedAt,
        long durationMs) {

    public double rowsPerSecond() {
        return durationMs > 0 ? (double) rowsProcessed / durationMs * 1000 : 0;
    }
}
