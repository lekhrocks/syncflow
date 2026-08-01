package com.syncflow.core.cdc;

import java.time.Instant;

public record EventMetadata(
        long eventNumber,
        Instant capturedAt,
        long captureLatencyMs) {
}
