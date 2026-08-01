package com.syncflow.core.governance;

import java.time.Duration;

public record RetentionPolicy(
        String entityType,
        Duration retentionDuration,
        String action) {

    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_ARCHIVE = "ARCHIVE";
    public static final String ACTION_ANONYMIZE = "ANONYMIZE";

    public static RetentionPolicy auditLogs() {
        return new RetentionPolicy("AUDIT_LOG", Duration.ofDays(365), ACTION_ARCHIVE);
    }

    public static RetentionPolicy pipelineHistory() {
        return new RetentionPolicy("PIPELINE_VERSION", Duration.ofDays(180), ACTION_DELETE);
    }

    public static RetentionPolicy deadLetterEvents() {
        return new RetentionPolicy("DLQ", Duration.ofDays(90), ACTION_DELETE);
    }

    public static RetentionPolicy piiData() {
        return new RetentionPolicy("PII", Duration.ofDays(365), ACTION_ANONYMIZE);
    }
}
