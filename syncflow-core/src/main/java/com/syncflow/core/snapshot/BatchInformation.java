package com.syncflow.core.snapshot;

public record BatchInformation(
        int batchNumber,
        int batchSize,
        String sourceTable,
        String cursor) {
}
