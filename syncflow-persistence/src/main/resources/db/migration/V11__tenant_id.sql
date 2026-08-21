-- Multi-tenant data isolation: every domain table carries a tenant_id so
-- repositories can scope reads/writes by the request's tenant context.
-- The single default tenant value is TenantId.DEFAULT.value()
-- ("00000000-0000-0000-0000-000000000000") so context-less rows and
-- request-path data land in the same tenant.
ALTER TABLE connections
    ADD COLUMN tenant_id VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE pipeline_designs
    ADD COLUMN tenant_id VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE pipeline_design_versions
    ADD COLUMN tenant_id VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE dead_letter_events
    ADD COLUMN tenant_id VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE processed_events
    ADD COLUMN tenant_id VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

CREATE INDEX idx_connections_tenant    ON connections(tenant_id);
CREATE INDEX idx_pipeline_designs_tenant ON pipeline_designs(tenant_id);
CREATE INDEX idx_dlq_tenant            ON dead_letter_events(tenant_id);
CREATE INDEX idx_processed_events_tenant ON processed_events(tenant_id);
