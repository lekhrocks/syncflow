-- Per-chunk resume checkpoints for parallel PK-range snapshot (F15).
-- Adds chunk_index so each chunk of a table resumes from its own cursor.
ALTER TABLE snapshot_checkpoints
    ADD COLUMN IF NOT EXISTS chunk_index INTEGER NOT NULL DEFAULT 0;

-- Widen the unique key to include the chunk so multiple chunks of one table
-- can hold distinct checkpoints concurrently.
ALTER TABLE snapshot_checkpoints
    DROP CONSTRAINT IF EXISTS uq_checkpoint_pipeline_table;

ALTER TABLE snapshot_checkpoints
    ADD CONSTRAINT uq_checkpoint_pipeline_table_chunk
        UNIQUE (tenant_id, pipeline_id, source_table, chunk_index);