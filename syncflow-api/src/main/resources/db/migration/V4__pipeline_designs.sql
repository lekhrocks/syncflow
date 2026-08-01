-- Pipeline designs: stores the full PipelineDesign from the designer service
CREATE TABLE IF NOT EXISTS pipeline_designs (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    source          JSONB NOT NULL,
    destination     JSONB NOT NULL,
    table_mappings  JSONB NOT NULL DEFAULT '[]',
    settings        JSONB NOT NULL DEFAULT '{}',
    version         INTEGER NOT NULL DEFAULT 1,
    created_by      VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipeline_designs_status ON pipeline_designs(status);
CREATE INDEX idx_pipeline_designs_created_at ON pipeline_designs(created_at DESC);

-- Pipeline design versions: stores each historical version for rollback support
CREATE TABLE IF NOT EXISTS pipeline_design_versions (
    id              BIGSERIAL PRIMARY KEY,
    pipeline_id     VARCHAR(36) NOT NULL REFERENCES pipeline_designs(id) ON DELETE CASCADE,
    version         INTEGER NOT NULL,
    snapshot        JSONB NOT NULL,
    saved_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(pipeline_id, version)
);

CREATE INDEX idx_pipeline_design_versions_pipeline ON pipeline_design_versions(pipeline_id);
