# Runbook: Checkpoint Corruption

> **Severity:** SEV1 — Data integrity at risk, possible duplicate or missed events  
> **Owner:** Platform Engineering  

## Symptoms
- Snapshot resumes from wrong batch (missing or duplicate rows in destination)
- Error logs: "Checkpoint batch number invalid"
- `GET /api/snapshots/{id}` shows negative rowsProcessed
- `CheckpointStore` integrity check fails (FOREIGN_KEY violation, or NULL batch number)

## Possible Causes
1. Manual database edit (operator deleted/updated checkpoint row)
2. Storage corruption (disk failure, file system error)
3. Concurrent writer to checkpoint store (lost update)
4. Database rollback or restore from inconsistent backup
5. Race condition between checkpoint save and snapshot completion

## Diagnosis

```bash
# Inspect checkpoint store
psql -h {pg_host} -U syncflow -c "
  SELECT pipeline_id, source_table, last_batch_number, rows_processed, cursor
  FROM checkpoints
  WHERE pipeline_id = '{pipeline_id}'
  ORDER BY source_table
"

# Check for invalid batch numbers
psql -h {pg_host} -U syncflow -c "
  SELECT * FROM checkpoints 
  WHERE last_batch_number < 0 OR rows_processed < 0
"

# Verify against in-memory state
curl -u admin:$TOKEN http://syncflow.example.com/api/snapshots/{id} | jq '.progress'

# Recent changes to checkpoint table
psql -h {pg_host} -U syncflow -c "
  SELECT * FROM pg_stat_user_tables WHERE relname = 'checkpoints'
"
```

## Recovery Steps

### Step 1: Stop the affected pipeline immediately
```bash
# Cancel running snapshot
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/snapshots/{id}/cancel

# Stop CDC
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/pipelines/{id}/capture/stop

# Stop sync
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/pipelines/{id}/sync/stop
```

### Step 2: Backup current state
```bash
# Export current destination state for forensic analysis
./scripts/backup.sh

# Save checkpoint table
psql -h {pg_host} -U syncflow -c "
  COPY checkpoints TO '/tmp/checkpoints_backup.csv' CSV HEADER
"
```

### Step 3: Validate the corruption
```bash
# Check destination data integrity
psql -h {dest_host} -c "
  SELECT COUNT(*) FROM {dest_table}
"
# Compare with source
psql -h {source_host} -c "
  SELECT COUNT(*) FROM {source_table}
"

# Use AI Copilot to identify data discrepancies
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/ai/review \
  -d '{"pipelineId": "{id}"}'
```

### Step 4: Reset checkpoint
```bash
# Option A: Full re-snapshot (safest for massive corruption)
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/pipelines/{id}/snapshot

# Option B: Reset specific table to specific batch
psql -h {pg_host} -U syncflow -c "
  DELETE FROM checkpoints WHERE pipeline_id = '{id}' AND source_table = '{table}'
"
# Re-run snapshot to recreate checkpoint
```

### Step 5: Resume sync
```bash
# Restart CDC with last known good offset
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/pipelines/{id}/capture/start

# Restart sync
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/pipelines/{id}/sync/start

# Monitor for data consistency
watch -n 30 "psql -h {dest_host} -c 'SELECT COUNT(*) FROM {dest_table}'"
```

## Escalation
- **SEV1 immediately** if data loss or duplication is confirmed
- Page **Platform Lead** AND **Data Engineering Lead**
- Engage customers if data integrity is affected
- Open architecture review for race condition analysis

## Post-Incident
- [ ] Root cause analysis (race condition? disk failure? operator error?)
- [ ] Add `version` column to checkpoint table for optimistic locking
- [ ] Add automated checkpoint integrity check (runs hourly)
- [ ] Add alert for checkpoint writes that skip batch numbers
- [ ] Document any data discrepancies in incident postmortem
