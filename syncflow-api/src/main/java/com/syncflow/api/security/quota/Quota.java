package com.syncflow.api.security.quota;

import java.util.Map;

public record Quota(Map<Metric, Long> limits) {

    public enum Metric {
        CONNECTIONS, PIPELINES, RUNNING_JOBS, STORAGE_BYTES, AI_REQUESTS, API_CALLS, SNAPSHOT_SIZE, CDC_THROUGHPUT
    }

    public long limit(Metric metric) {
        return limits.getOrDefault(metric, -1L);
    }

    public static Quota defaults() {
        return new Quota(Map.of(
                Metric.CONNECTIONS, 100L,
                Metric.PIPELINES, 50L,
                Metric.RUNNING_JOBS, 10L,
                Metric.STORAGE_BYTES, 10_000_000_000L,
                Metric.AI_REQUESTS, 1000L,
                Metric.API_CALLS, 100_000L));
    }
}
