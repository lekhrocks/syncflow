package com.syncflow.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    private MetricsCollector() {
    }

    public static void recordOperation(String operation, String status, long durationMs) {
        log.info("METRIC operation={} status={} durationMs={}", operation, status, durationMs);
    }

    public static void recordPipelineStatus(String pipelineId, String status) {
        log.info("METRIC pipeline={} status={}", pipelineId, status);
    }
}
