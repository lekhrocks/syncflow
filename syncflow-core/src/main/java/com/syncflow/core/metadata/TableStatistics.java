package com.syncflow.core.metadata;

public record TableStatistics(
        long rowCountEstimate,
        long totalSizeBytes,
        long dataSizeBytes,
        long indexSizeBytes,
        long liveTuples,
        long deadTuples) {

    public static TableStatistics unknown() {
        return new TableStatistics(0, 0, 0, 0, 0, 0);
    }
}
