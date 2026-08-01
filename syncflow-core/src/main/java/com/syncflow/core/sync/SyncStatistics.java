package com.syncflow.core.sync;

public record SyncStatistics(
        long totalEvents,
        long processedEvents,
        long failedEvents,
        long skippedEvents,
        long retries,
        long dlqCount,
        long avgLatencyMs) {
}
