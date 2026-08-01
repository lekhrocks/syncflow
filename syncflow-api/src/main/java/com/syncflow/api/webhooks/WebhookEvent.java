package com.syncflow.api.webhooks;

import java.time.Instant;

public record WebhookEvent(
        String id,
        String type,
        String pipelineId,
        String connectionId,
        String payload,
        Instant timestamp) {

    public static final String TYPE_PIPELINE_CREATED = "pipeline.created";
    public static final String TYPE_PIPELINE_DELETED = "pipeline.deleted";
    public static final String TYPE_PIPELINE_FAILED = "pipeline.failed";
    public static final String TYPE_SNAPSHOT_COMPLETED = "snapshot.completed";
    public static final String TYPE_SNAPSHOT_FAILED = "snapshot.failed";
    public static final String TYPE_CDC_STOPPED = "cdc.stopped";
    public static final String TYPE_SYNC_FAILED = "sync.failed";
    public static final String TYPE_CONNECTION_LOST = "connection.lost";
    public static final String TYPE_SCHEMA_CHANGED = "schema.changed";
    public static final String TYPE_WORKFLOW_COMPLETED = "workflow.completed";
    public static final String TYPE_AGENT_OFFLINE = "agent.offline";
}
