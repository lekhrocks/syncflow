-- flyway:ignoreTransaction
-- V17: Supporting tables and functions for multi-region geo-replication.
--
-- Publication and replication slot creation is intentionally omitted from this
-- migration.  Those objects require wal_level=logical and a transaction-free
-- context, so they are created by the application at startup when
-- syncflow.region.replication-enabled=true.  This migration is therefore safe
-- for test databases and single-region deployments.

-- Track replication slot history for debugging and auditing
CREATE TABLE IF NOT EXISTS replication_log (
  id            BIGSERIAL PRIMARY KEY,
  slot_name     TEXT      NOT NULL,
  region        TEXT      NOT NULL,
  status        TEXT      NOT NULL,
  lag_bytes     BIGINT,
  last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_replication_log_slot_name ON replication_log(slot_name);
CREATE INDEX IF NOT EXISTS idx_replication_log_region    ON replication_log(region);

-- Returns the current replication lag for all syncflow logical slots.
-- Called by the health-check and metrics pipelines on the primary.
CREATE OR REPLACE FUNCTION check_replication_status()
RETURNS TABLE(
  slot_name           TEXT,
  region              TEXT,
  active              BOOLEAN,
  restart_lsn         TEXT,
  confirmed_flush_lsn TEXT,
  lag_bytes           BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    rs.slot_name::TEXT,
    CASE
      WHEN rs.slot_name LIKE '%eu_west%'      THEN 'eu-west-1'
      WHEN rs.slot_name LIKE '%ap_southeast%' THEN 'ap-southeast-1'
      ELSE 'unknown'
    END,
    rs.active,
    rs.restart_lsn::TEXT,
    rs.confirmed_flush_lsn::TEXT,
    pg_wal_lsn_diff(pg_current_wal_lsn(), rs.restart_lsn)::BIGINT
  FROM pg_replication_slots rs
  WHERE rs.slot_type = 'logical'
    AND rs.slot_name LIKE 'syncflow%';
END;
$$ LANGUAGE plpgsql;

-- Drops a logical replication slot safely (no-op if slot does not exist).
-- The parameter is named p_slot_name to avoid shadowing the pg_replication_slots column.
CREATE OR REPLACE FUNCTION drop_replication_slot_safe(p_slot_name TEXT)
RETURNS VOID AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = p_slot_name) THEN
    PERFORM pg_drop_replication_slot(p_slot_name);
    INSERT INTO replication_log (slot_name, region, status)
    VALUES (p_slot_name, 'unknown', 'dropped');
  END IF;
END;
$$ LANGUAGE plpgsql;

-- Audit trail for replication lifecycle events
-- (populated by the application on failover, promotion, subscription changes, etc.)
CREATE TABLE IF NOT EXISTS replication_events (
  id          BIGSERIAL PRIMARY KEY,
  event_type  TEXT      NOT NULL,  -- 'subscription_created', 'subscription_disabled', 'promotion', 'failover'
  region      TEXT      NOT NULL,
  description TEXT,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_replication_events_type       ON replication_events(event_type);
CREATE INDEX IF NOT EXISTS idx_replication_events_region     ON replication_events(region);
CREATE INDEX IF NOT EXISTS idx_replication_events_created_at ON replication_events(created_at);
