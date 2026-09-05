-- Partition debezium_offsets by tenant_id to eliminate cross-tenant contention.
--
-- BACKGROUND
-- ----------
-- V13 created debezium_offsets with a single offset_key BYTEA PRIMARY KEY.
-- All tenants write to one table; at scale this becomes a hot spot.
-- The JdbcOffsetBackingStore issues upserts with ON CONFLICT (offset_key),
-- so the PRIMARY KEY constraint on offset_key must remain in place.
--
-- APPROACH
-- --------
-- 1. Add a tenant_id UUID column (NOT NULL, default = system tenant UUID).
-- 2. Keep offset_key as part of the key; the store's ON CONFLICT clause is
--    updated in application code (see JdbcOffsetBackingStore) to also carry
--    tenant_id so each tenant's offsets are isolated at the DB level.
-- 3. Recreate the table with a composite PRIMARY KEY (tenant_id, offset_key)
--    and PARTITION BY LIST (tenant_id), then install a catch-all DEFAULT
--    partition. Tenant-specific partitions are added on demand when tenants
--    are provisioned (see create_debezium_offsets_partition() below).
-- 4. Backfill existing rows into the default (system-tenant) partition — they
--    have no tenant context in the binary key so assigning the system UUID is
--    safe; these rows belong to integrations started before multi-tenancy.
--
-- SAFETY
-- ------
-- * The migration is transactional. If any step fails, Flyway rolls back.
-- * DROP TABLE is executed only after the data copy succeeds (INSERT … SELECT).
-- * JdbcOffsetBackingStore must use the new conflict target
--   (tenant_id, offset_key) after this migration is applied — see the
--   accompanying application-code change.

BEGIN;

-- Step 1: Materialize current data into a staging copy so we have a rollback
--         point even if subsequent steps fail mid-way.
CREATE TABLE debezium_offsets_bak AS
SELECT * FROM debezium_offsets;

-- Step 2: Create the new partitioned table.
--         PRIMARY KEY is (tenant_id, offset_key) — composite to enable the
--         tenant-scoped ON CONFLICT clause in JdbcOffsetBackingStore.
CREATE TABLE debezium_offsets_new (
    tenant_id   UUID  NOT NULL
                      DEFAULT '00000000-0000-0000-0000-000000000000',
    offset_key  BYTEA NOT NULL,
    offset_data BYTEA,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, offset_key)
) PARTITION BY LIST (tenant_id);

-- Step 3: Default partition — catches every tenant_id not explicitly listed.
--         New tenants land here until ops provisions a dedicated partition.
CREATE TABLE debezium_offsets_default
    PARTITION OF debezium_offsets_new DEFAULT;

-- Step 4: Copy existing rows. All pre-migration rows receive the system-tenant
--         UUID because offset_key is opaque BYTEA from Kafka Connect — there is
--         no tenant information encoded in the binary key itself.
INSERT INTO debezium_offsets_new (tenant_id, offset_key, offset_data, updated_at)
SELECT
    '00000000-0000-0000-0000-000000000000'::UUID,
    offset_key,
    offset_data,
    updated_at
FROM debezium_offsets;

-- Step 5: Verify the copy before destroying the original.
DO $$
DECLARE
    src_count  BIGINT;
    dest_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO src_count  FROM debezium_offsets;
    SELECT COUNT(*) INTO dest_count FROM debezium_offsets_new;
    IF dest_count < src_count THEN
        RAISE EXCEPTION
            'Row count mismatch after copy: src=% dest=% — aborting migration',
            src_count, dest_count;
    END IF;
END;
$$;

-- Step 6: Swap tables atomically.
DROP TABLE debezium_offsets;
ALTER TABLE debezium_offsets_new RENAME TO debezium_offsets;

-- Step 7: Supporting index for efficient tenant-scoped queries
--         (partition pruning already handles most of this, but the index helps
--         within large partitions).
CREATE INDEX idx_debezium_offsets_tenant_updated
    ON debezium_offsets (tenant_id, updated_at DESC);

-- Step 8: Clean up staging backup (only reached on success).
DROP TABLE debezium_offsets_bak;

-- Step 9: Helper function — call this when provisioning a new tenant to give
--         them a dedicated partition before their first CDC pipeline starts.
--         Example: SELECT create_debezium_offsets_partition('acme-corp-uuid-here');
CREATE OR REPLACE FUNCTION create_debezium_offsets_partition(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    partition_name TEXT;
BEGIN
    partition_name := 'debezium_offsets_' || replace(p_tenant_id::TEXT, '-', '_');
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF debezium_offsets FOR VALUES IN (%L)',
        partition_name,
        p_tenant_id
    );
END;
$$;

COMMENT ON TABLE debezium_offsets IS
    'Debezium offset store partitioned by tenant_id (LIST). '
    'Each row holds one Kafka Connect offset key→value pair for a CDC connector. '
    'Existing pre-migration rows use tenant_id = 00000000-0000-0000-0000-000000000000. '
    'Call create_debezium_offsets_partition(uuid) when provisioning new tenants.';

COMMENT ON FUNCTION create_debezium_offsets_partition(UUID) IS
    'Creates a dedicated LIST partition of debezium_offsets for the given tenant UUID. '
    'Safe to call multiple times (IF NOT EXISTS). '
    'Call once per tenant at provisioning time.';

COMMIT;
