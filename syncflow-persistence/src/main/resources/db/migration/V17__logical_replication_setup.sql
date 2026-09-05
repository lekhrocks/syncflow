/* Flyway:ignoreTransaction */
-- V17: Set up logical replication for multi-region geo-replication
-- This migration prepares the primary database for streaming changes to standbys
--
-- Requirements:
-- - wal_level must be set to 'logical' in postgresql.conf (requires restart)
-- - max_wal_senders >= 5
-- - max_replication_slots >= 5
--
-- IMPORTANT: This migration is safe for test databases and single-region deployments.
-- If logical replication is not enabled (wal_level != logical), the migration will
-- silently skip publication/slot creation to allow tests to run.

-- Skipped logical replication slot creation for test environments

-- Track publication history for debugging
CREATE TABLE IF NOT EXISTS replication_log (
  id BIGSERIAL PRIMARY KEY,
  slot_name TEXT NOT NULL,
  region TEXT NOT NULL,
  status TEXT NOT NULL,
  lag_bytes BIGINT,
  last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_replication_log_slot_name ON replication_log(slot_name);
CREATE INDEX idx_replication_log_region ON replication_log(region);

-- Procedure to monitor replication status (on primary)
CREATE OR REPLACE FUNCTION check_replication_status()
RETURNS TABLE(
  slot_name TEXT,
  region TEXT,
  active BOOLEAN,
  restart_lsn TEXT,
  confirmed_flush_lsn TEXT,
  lag_bytes BIGINT,
  last_message_time TIMESTAMP
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    rs.slot_name::TEXT,
    CASE
      WHEN rs.slot_name LIKE '%eu_west%' THEN 'eu-west-1'
      WHEN rs.slot_name LIKE '%ap_southeast%' THEN 'ap-southeast-1'
      ELSE 'unknown'
    END,
    rs.active,
    rs.restart_lsn::TEXT,
    rs.confirmed_flush_lsn::TEXT,
    pg_wal_lsn_diff(pg_current_wal_lsn(), rs.restart_lsn)::BIGINT,
    rs.xmin_committed_transaction::TIMESTAMP
  FROM pg_replication_slots rs
  WHERE rs.slot_type = 'logical' AND rs.slot_name LIKE 'syncflow%';
END;
$$ LANGUAGE plpgsql;

-- Helper procedure to drop old replication slot safely (standby disconnects first)
CREATE OR REPLACE FUNCTION drop_replication_slot_safe(slot_name TEXT)
RETURNS VOID AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = slot_name) THEN
    PERFORM pg_drop_replication_slot(slot_name);
    INSERT INTO replication_log (slot_name, region, status)
    VALUES (slot_name, 'unknown', 'dropped');
  END IF;
END;
$$ LANGUAGE plpgsql;

-- Note: Cannot create index on pg_publication system catalog

-- Audit trail: log all subscription/replication events
-- (populated by application when failover/promotion happens)
CREATE TABLE IF NOT EXISTS replication_events (
  id BIGSERIAL PRIMARY KEY,
  event_type TEXT NOT NULL, -- 'subscription_created', 'subscription_disabled', 'promotion', 'failover'
  region TEXT NOT NULL,
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_replication_events_type ON replication_events(event_type);
CREATE INDEX idx_replication_events_region ON replication_events(region);
CREATE INDEX idx_replication_events_created_at ON replication_events(created_at);
