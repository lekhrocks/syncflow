# Runbook: Checkpoint Corruption (Duplicate Events)

> **Severity:** SEV1 — Data integrity at risk, possible duplicate or missed events  
> **Owner:** Platform Engineering

## Symptoms
- Snapshot resumes from wrong batch (missing or duplicate rows in destination)
- Error logs: "Checkpoint batch number invalid"
- `GET /api/snapshots/{id}` shows inconsistent `rowsProcessed` vs `totalRows`
- `CheckpointStore` data shows negative batch numbers or gaps
- Destination has more rows than source (duplicates) or less (missing)

## Possible Causes
1. Race condition: checkpoint saved before batch fully committed to destination
2. Manual database edit (operator deleted/updated checkpoint data)
3. Storage corruption (disk failure, file system error)
4. Database restore from inconsistent backup
5. Schema migration changed checkpoint table structure
6. Two snapshots running concurrently for the same pipeline

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
  OR last_batch_number IS NULL
"

# Verify data consistency
psql -h {dest_host} -c "
  SELECT COUNT(*) FROM {dest_table}
"
psql -h {source_host} -c "
  SELECT COUNT(*) FROM {source_table}
"

# Compare row counts
DEST_COUNT=$(psql -h {dest_host} -tAc "SELECT COUNT(*) FROM {dest_table}")
SRC_COUNT=$(psql -h {source_host} -tAc "SELECT COUNT(*) FROM {source_table}")
echo "Destination: $DEST_COUNT, Source: $SRC_COUNT, Diff: $(($SRC_COUNT - $DEST_COUNT))"

# Check if checkpoint exists for the current execution
curl -u admin:$TOKEN http://syncflow.example.com/api/snapshots/{id} | jq '.progress'
```

## Recovery Steps

### Step 1: Stop the affected pipeline
```bash
# Cancel running snapshot (preserves checkpoints)
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/snapshots/{id}/cancel

# Stop CDC and sync
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/capture/stop
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/sync/stop
```

### Step 2: Determine the action

## Option A: Full re-snapshot (if corruption is complete)
```bash
# Delete all checkpoints for this pipeline
psql -h {pg_host} -U syncflow -c "
  DELETE FROM checkpoints WHERE pipeline_id = '{pipeline_id}'
"

# Start fresh snapshot
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/snapshot
```

## Option B: Resume from last known good batch
```bash
# Set checkpoint manually to specific batch
psql -h {pg_host} -U syncflow -c "
  UPDATE checkpoints 
  SET last_batch_number = 42, rows_processed = 4200
  WHERE pipeline_id = '{pipeline_id}' AND source_table = '{table}'
"

# Resume snapshot
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/snapshot
```

### Step 3: Verify data consistency after recovery
```bash
# Monitor snapshot progress
watch -n 30 "curl -s -u admin:\$TOKEN http://syncflow.example.com/api/snapshots/{id} | jq '.progress'"

# After completion, verify counts match
SRC=$(psql -h {source_host} -tAc "SELECT COUNT(*) FROM {source_table}")
DEST=$(psql -h {dest_host} -tAc "SELECT COUNT(*) FROM {dest_table}")
echo "Source: $SRC, Destination: $DEST"

if [ "$SRC" == "$DEST" ]; then
  echo "✅ Row counts match"
else
  echo "❌ Row counts differ: source=$SRC dest=$DEST diff=$(($SRC - $DEST))"
fi
```

### Step 4: Restart CDC and sync
```bash
# Resume CDC from last known offset
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/capture/start

# Resume sync
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/sync/start
```

## Escalation
- **SEV1 immediately** if data loss or duplication is confirmed
- Page **Platform Lead** AND **Data Engineering Lead**
- Engage customers if data integrity is affected
- Open architecture review for race condition analysis

## Post-Incident
- [ ] Root cause analysis (race condition? operator error?)
- [ ] Add checkpoint write verification (read-after-write check)
- [ ] Add automated consistency checks on checkpoint load
- [ ] Implement optimistic locking on checkpoint writes
- [ ] Add chaos test for checkpoint corruption recovery
