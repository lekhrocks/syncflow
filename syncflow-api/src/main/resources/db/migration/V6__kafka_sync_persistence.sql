-- Dead Letter Queue: persists failed CDC events for inspection and replay
CREATE TABLE IF NOT EXISTS dead_letter_events (
    id              VARCHAR(36) PRIMARY KEY,
    pipeline_id     VARCHAR(36) NOT NULL,
    event_id        VARCHAR(36) NOT NULL,
    event_data      JSONB NOT NULL,
    error_message   TEXT,
    failure_type    VARCHAR(20) NOT NULL DEFAULT 'TRANSIENT',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    replayed_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_dlq_pipeline_id  ON dead_letter_events(pipeline_id);
CREATE INDEX idx_dlq_created_at   ON dead_letter_events(created_at DESC);
CREATE INDEX idx_dlq_event_id     ON dead_letter_events(event_id);

-- Idempotency store: tracks processed event IDs to prevent duplicate processing
-- TTL-managed: entries older than retention_days are purged by a scheduled job
CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(36) PRIMARY KEY,
    pipeline_id     VARCHAR(36) NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_events_pipeline  ON processed_events(pipeline_id);
CREATE INDEX idx_processed_events_time      ON processed_events(processed_at DESC);
