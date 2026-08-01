# Runbook: Pipeline Failed

> **Severity:** SEV2 — Data ingestion interrupted  
> **Owner:** Platform Engineering  

## Symptoms
- `PipelineFailureRateHigh` alert firing in Prometheus
- `GET /api/snapshots/{id}` returns status `FAILED`
- `GET /api/sync/jobs/{id}` shows `failedEvents` > 0
- Users report stale data in destination

## Possible Causes
1. Source database connection lost mid-snapshot
2. Destination database out of space
3. Schema change broke mapping (column dropped/renamed)
4. Authentication credentials rotated
5. Network partition between control plane and agent
6. Plugin exception in custom transformation

## Diagnosis

```bash
# Check pipeline status
curl -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id} | jq .status

# Check snapshot errors
curl -u admin:$TOKEN http://syncflow.example.com/api/snapshots/{id} | jq '.errors[]'

# Check sync errors
curl -u admin:$TOKEN http://syncflow.example.com/api/sync/jobs/{id} | jq '.statistics.failedEvents'

# Check DLQ
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq?pipelineId={id} | jq '.[0]'

# Check source connection health
curl -u admin:$TOKEN http://syncflow.example.com/api/connections/{sourceId}/health

# View logs (last 100 lines from running pod)
kubectl logs -n syncflow -l app=syncflow --tail=100 | grep -i error | tail -20
```

## Recovery Steps

### Step 1: Identify the root cause
```bash
# Check logs for specific error
kubectl logs -n syncflow -l app=syncflow --tail=500 | grep -A 5 "{pipeline_id}"
```

### Step 2: For source DB issues
```bash
# Verify source DB connectivity
nc -zv {source_host} {source_port}

# Re-test connection
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/connections/test \
  -d '{"host":"...","port":5432,"database":"..."}'
```

### Step 3: For destination DB issues
```bash
# Check destination disk space
df -h /var/lib/postgresql  # SSH to destination DB host

# Free up space if needed
# Or resize volume
```

### Step 4: For schema mismatch
```bash
# Refresh metadata
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/connections/{connId}/metadata/refresh

# Inspect new schema
curl -u admin:$TOKEN http://syncflow.example.com/api/connections/{connId}/schemas/public/tables

# Update pipeline mappings via UI or PUT /api/pipelines/{id}
```

### Step 5: Restart the pipeline
```bash
# Replay DLQ events
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/dlq/{id}/replay

# Or restart from checkpoint
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/pipelines/{id}/snapshot
```

## Escalation
- If root cause not found within 30 minutes: page **Platform Lead** (Slack: `#syncflow-incidents`)
- If data loss occurred: open SEV1 incident, notify affected customers via status page
- If recurrence within 24 hours: open architecture review ticket

## Post-Incident
- [ ] Update `docs/adr/` if architectural change needed
- [ ] Add regression test to `e2e/` suite
- [ ] Review this runbook against actual fix path
- [ ] Update alert thresholds if needed
