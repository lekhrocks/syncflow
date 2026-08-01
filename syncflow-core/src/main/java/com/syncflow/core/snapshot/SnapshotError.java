package com.syncflow.core.snapshot;

import java.time.Instant;

public record SnapshotError(
        String code,
        String message,
        int batchNumber,
        Instant timestamp) {
}
