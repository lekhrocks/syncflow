-- Count how many times each DLQ event has been replayed so the audit trail
-- shows replay history, not just the last replay timestamp.
ALTER TABLE dead_letter_events
    ADD COLUMN replay_count INTEGER NOT NULL DEFAULT 0;