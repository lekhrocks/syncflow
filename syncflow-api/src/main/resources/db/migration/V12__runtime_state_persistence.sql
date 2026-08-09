-- Runtime state persistence: durable storage for the previously in-process
-- ConcurrentHashMap state stores (snapshots, sync jobs, workflows, quotas,
-- audit records, API keys, agents, alerts) so runtime state survives restarts.
-- tenant_id follows V11 conventions: context-less rows and request-path data
-- land in the single default tenant.

-- Snapshot jobs: full job payload (status/progress/stats/errors) as JSONB plus
-- denormalized status/pipeline columns for tenant-scoped queries.
CREATE TABLE IF NOT EXISTS snapshot_jobs (
    id           VARCHAR(36) PRIMARY KEY,
    tenant_id    VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    pipeline_id  VARCHAR(36) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_snapshot_jobs_tenant   ON snapshot_jobs(tenant_id);
CREATE INDEX idx_snapshot_jobs_pipeline ON snapshot_jobs(pipeline_id);
CREATE INDEX idx_snapshot_jobs_status   ON snapshot_jobs(status);

-- Snapshot resume checkpoints (one per pipeline+source table per tenant).
CREATE TABLE IF NOT EXISTS snapshot_checkpoints (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    pipeline_id       VARCHAR(36) NOT NULL,
    source_table      VARCHAR(255) NOT NULL,
    last_batch_number INTEGER NOT NULL,
    rows_processed    BIGINT NOT NULL,
    cursor_pos        VARCHAR(4096),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_checkpoint_pipeline_table UNIQUE (tenant_id, pipeline_id, source_table)
);

-- Sync jobs: one live job per tenant+pipeline (matches the orchestrator's
-- in-memory tenant-scoped map key). Statistics stored as JSONB.
CREATE TABLE IF NOT EXISTS sync_jobs (
    id           VARCHAR(36) PRIMARY KEY,
    tenant_id    VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    pipeline_id  VARCHAR(36) NOT NULL,
    state        VARCHAR(20) NOT NULL,
    statistics   JSONB NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_sync_job_tenant_pipeline UNIQUE (tenant_id, pipeline_id)
);

-- Workflow instances: task graph and executions stored as JSONB.
CREATE TABLE IF NOT EXISTS workflow_instances (
    id           VARCHAR(36) PRIMARY KEY,
    tenant_id    VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    pipeline_id  VARCHAR(36) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    tasks        JSONB NOT NULL,
    executions   JSONB NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Tenant quotas: one row per tenant, limits map as JSONB.
CREATE TABLE IF NOT EXISTS quotas (
    tenant_id  VARCHAR(36) PRIMARY KEY,
    limits     JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Enterprise audit records (GDPR right-to-delete via anonymize).
CREATE TABLE IF NOT EXISTS audit_records (
    id            UUID PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    actor         VARCHAR(255),
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id   VARCHAR(255),
    details       TEXT,
    ip_address    VARCHAR(45),
    suspicious    BOOLEAN NOT NULL DEFAULT FALSE,
    event_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_records_tenant ON audit_records(tenant_id);

-- API keys: hashed value unique; revoke/expiry drive isActive().
CREATE TABLE IF NOT EXISTS api_keys (
    id         UUID PRIMARY KEY,
    tenant_id  VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    hashed_key VARCHAR(64) NOT NULL,
    prefix     VARCHAR(16),
    label      VARCHAR(255),
    scope      VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_api_keys_hash UNIQUE (hashed_key)
);

-- Agent fleet: hardware metrics and capabilities as JSONB.
CREATE TABLE IF NOT EXISTS agents (
    id             VARCHAR(36) PRIMARY KEY,
    tenant_id      VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    version        VARCHAR(50),
    status         VARCHAR(20) NOT NULL,
    capabilities   JSONB NOT NULL,
    labels         JSONB NOT NULL,
    environment    VARCHAR(50),
    region         VARCHAR(50),
    hostname       VARCHAR(255),
    hardware       JSONB NOT NULL,
    registered_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Ops alerts: live incidents surfaced on the dashboard.
CREATE TABLE IF NOT EXISTS alert_events (
    id            VARCHAR(50) PRIMARY KEY,
    tenant_id     VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    name          VARCHAR(255) NOT NULL,
    message       TEXT,
    severity      VARCHAR(20) NOT NULL,
    source        VARCHAR(255),
    pipeline_id   VARCHAR(36),
    connection_id VARCHAR(36),
    event_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
