# Database Migration Strategy

> **Version:** 1.0  
> **Applies to:** SyncFlow Control Plane (PostgreSQL 16)

---

## Principles

1. **Forward-only**: Migrations are additive. Never modify or delete a migration that has been applied to production.
2. **Idempotent**: `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX CONCURRENTLY` — safe to re-run.
3. **Backward compatible**: New columns have defaults or are nullable. Old code continues working.
4. **Zero-downtime**: Migrations must not acquire `ACCESS EXCLUSIVE` locks that block reads/writes.

## Migration Lifecycle

```
┌─────────────────────┐
│  Developer writes   │  V3__add_description.sql
│  new migration      │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│  CI validates it    │  Flyway checksum check + Testcontainers test
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│  Deploy to staging  │  Migration runs in staging, validated by smoke tests
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│  Deploy to prod     │  Migration runs as part of deployment
│  (zero-downtime)    │  Old pods + new migrations are compatible
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│  Verify & monitor   │  pg_stat_activity, slow queries, error logs
└─────────────────────┘
```

## Zero-Downtime Migration Rules

### ✅ Safe (non-blocking)

| Operation | Example | Lock |
|-----------|---------|:----:|
| `CREATE TABLE` | `V1__init.sql` | None |
| `CREATE INDEX CONCURRENTLY` | `idx_pipelines_status` | `SHARE UPDATE EXCLUSIVE` |
| `ADD COLUMN ... DEFAULT NULL` | `ALTER TABLE ADD COLUMN x TEXT` | `ACCESS EXCLUSIVE` (brief) |
| `CREATE TABLE IF NOT EXISTS` | Idempotent re-runs | None |

### ⚠️ Requires care

| Operation | Risk | Mitigation |
|-----------|------|------------|
| `ADD COLUMN ... DEFAULT <value>` | Rewrites entire table, blocks reads | Add column as NULLABLE, backfill with batched UPDATEs |
| `ALTER COLUMN ... SET NOT NULL` | Fails if NULLs exist, blocks writes | Validate first, add in 2-step migration |
| `DROP COLUMN` | Clients may still reference it | Mark as deprecated first, drop in future migration |
| `CREATE INDEX` (without CONCURRENTLY) | Blocks writes on table | Always use `CREATE INDEX CONCURRENTLY`**

### ❌ Blocking (avoid in production)

- `ALTER TABLE ... RENAME COLUMN` — breaks running code
- `DROP TABLE` — breaks running queries
- `ALTER COLUMN ... TYPE` — full table rewrite
- `VACUUM FULL` — **ACCESS EXCLUSIVE** lock

## Migration Pattern: Add a Column

```sql
-- Step 1: Add column (nullable, zero-downtime)
ALTER TABLE pipelines ADD COLUMN IF NOT EXISTS description TEXT;

-- Step 2: Backfill data (background job, not a migration)
UPDATE pipelines SET description = name WHERE description IS NULL;

-- Step 3: (Future migration) Set NOT NULL after backfill verified
ALTER TABLE pipelines ALTER COLUMN description SET NOT NULL;
```

## Migration Pattern: Add an Index

```sql
-- Safe for production — does not block reads/writes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pipelines_name ON pipelines(name);
```

## Migration Verification Checklist

Pre-deployment:
- [ ] Migration has been tested on a staging database with real data volume
- [ ] Migration is idempotent (uses `IF NOT EXISTS`, `CONCURRENTLY`)
- [ ] Rollback SQL is documented (or a no-op)
- [ ] Migration takes < 5 seconds on production-sized dataset
- [ ] No `DROP TABLE`, `RENAME COLUMN`, or `ALTER COLUMN TYPE`

Post-deployment:
- [ ] `SELECT * FROM flyway_schema_history` shows new migration
- [ ] Hibernate `ddl-auto=validate` passes (automated in CI)
- [ ] pg_stat_activity shows no long-running locks from migration
- [ ] Application health check returns UP

## Rollback Strategy

| Scenario | Action |
|----------|--------|
| Migration fails during deploy | Deployment fails, no rollback needed — fix forward |
| Migration applied, bug found | Deploy new migration that reverts the change |
| Migration corrupts data | Restore from backup (RPO: 4 hours, RTO: 30 min) |
| Migration is too slow | Kill migration, fix forward with optimized SQL |

## Monitoring During Migration

```sql
-- Check running migrations
SELECT pid, state, query, wait_event, NOW() - query_start AS duration
FROM pg_stat_activity
WHERE query ILIKE '%flyway%' OR query ILIKE '%alter%' OR query ILIKE '%create index%';

-- Check lock conflicts
SELECT pg_database.datname, pg_locks.pid, pg_locks.mode,
       pg_locks.granted, pg_stat_activity.query
FROM pg_locks
JOIN pg_stat_activity ON pg_locks.pid = pg_stat_activity.pid
JOIN pg_database ON pg_locks.database = pg_database.oid
WHERE NOT pg_locks.granted;
```
