-- Active CDC captures: durable record replacing the in-memory ConcurrentHashMap
-- so capture state survives pod restarts and is shared across replicas.
-- Keyed by (tenant_id, pipeline_id) so tenants cannot collide.
-- id = tenantId(36) + ":" + pipelineId(36) = 73 chars, hence VARCHAR(128) not 64.
CREATE TABLE IF NOT EXISTS active_captures (
    id           VARCHAR(128) PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL,
    pipeline_id  VARCHAR(64) NOT NULL,
    status       VARCHAR(32) NOT NULL,
    offset_data  TEXT,
    started_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_active_captures_tenant   ON active_captures(tenant_id);
CREATE INDEX idx_active_captures_pipeline ON active_captures(pipeline_id);