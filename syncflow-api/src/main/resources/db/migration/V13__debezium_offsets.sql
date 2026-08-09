-- Debezium offset store: generic Kafka Connect key/value offsets (binary),
-- used by the connector module's JdbcOffsetBackingStore. Debezium keys its
-- offsets by connector namespace + partition, so the key is stored as opaque
-- bytes rather than pipeline_id. Survives pod restarts (unlike the old
-- /tmp FileOffsetBackingStore), preventing re-processing or missed events.
CREATE TABLE IF NOT EXISTS debezium_offsets (
    offset_key  BYTEA PRIMARY KEY,
    offset_data BYTEA,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
