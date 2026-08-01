-- CDC offset store: persists the last known WAL/LSN/binlog position per pipeline
-- so CDC can resume from the correct position after a restart.
CREATE TABLE IF NOT EXISTS cdc_offsets (
    pipeline_id     VARCHAR(36) PRIMARY KEY,
    connector_type  VARCHAR(20) NOT NULL,
    offset_data     JSONB NOT NULL DEFAULT '{}',
    saved_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cdc_offsets_connector ON cdc_offsets(connector_type);
