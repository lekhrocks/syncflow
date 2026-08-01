CREATE TABLE IF NOT EXISTS pipelines (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    source          JSONB NOT NULL,
    destination     JSONB NOT NULL,
    mapping         JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipelines_status ON pipelines(status);
CREATE INDEX idx_pipelines_created_at ON pipelines(created_at DESC);

CREATE TABLE IF NOT EXISTS pipeline_events (
    id              BIGSERIAL PRIMARY KEY,
    pipeline_id     VARCHAR(36) NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    reason          TEXT,
    timestamp       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipeline_events_pipeline_id ON pipeline_events(pipeline_id);
