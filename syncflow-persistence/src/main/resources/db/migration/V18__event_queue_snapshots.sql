-- V18: Event queue snapshots for pod crash recovery
-- Persists pending CDC events from SyncOrchestrator.eventQueues to survive pod crashes
-- Snapshot is taken on @PreDestroy (graceful shutdown) or significant failure
-- Rehydrated on startup by SyncOrchestrator.rehydrateFromDatabase()

CREATE TABLE IF NOT EXISTS event_queue_snapshots (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    pipeline_id VARCHAR(255) NOT NULL,
    event_count INTEGER NOT NULL,
    events_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    pod_name VARCHAR(255),
    reason VARCHAR(255)
);

-- Index for efficient lookup by pipeline and tenant
CREATE INDEX idx_event_queue_snapshots_pipeline_tenant ON event_queue_snapshots(pipeline_id, tenant_id);

-- Index for TTL cleanup (delete expired snapshots)
CREATE INDEX idx_event_queue_snapshots_expires_at ON event_queue_snapshots(expires_at);

-- Index for tenant isolation
CREATE INDEX idx_event_queue_snapshots_tenant_id ON event_queue_snapshots(tenant_id);
