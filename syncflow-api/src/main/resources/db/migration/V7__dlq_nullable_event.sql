-- Allow event_id and event_data to be NULL so the DLQ can store failures
-- that have no associated CDC event (e.g. connection-level errors).
ALTER TABLE dead_letter_events
    ALTER COLUMN event_id  DROP NOT NULL,
    ALTER COLUMN event_data DROP NOT NULL;
