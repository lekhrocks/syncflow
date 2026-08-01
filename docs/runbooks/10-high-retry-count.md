# Runbook: High Retry Count

> **Severity:** SEV3 — Recoverable errors accumulating  
> **Owner:** Platform Engineering

## Symptoms
- `HighRetryRate` alert firing (retries > 5/s for 5m)
- `GET /api/dashboard/metrics` shows elevated retry count
- Error logs: repeated same error for same event
- Performance impact: retries consume CPU cycles without progress

## Possible Causes
1. Destination database transient errors (connection timeout, lock contention)
2. Network packet loss or latency spiking
3. Destination table lock contention from concurrent operations
4. Authentication token expiring mid-session
5. Transient I/O errors on destination storage

## Diagnosis

```bash
# Check retry rate
curl -u admin:$TOKEN http://syncflow.example.com/api/sync/jobs | jq '.[].statistics.retries'

# Check error details in DLQ
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq '.[0] | {reason, retryCount, timestamp}'

# Group by error type
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq 'group_by(.reason.code) | .[] | {code: .[0].reason.code, count: length}'

# Check destination DB health
psql -h {dest_host} -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND wait_event IS NOT NULL;"

# Check network latency
ping -c 10 {dest_host} | tail -3
```

## Recovery Steps

### Step 1: Identify the error pattern
```bash
# Check if errors are transient or permanent
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq?pipelineId={id} | jq '.[] | {reason: .reason.message, retryable: .reason.retryable, retryCount: .retryCount}'
```

### Step 2: Fix the underlying cause
```bash
# For connection pool exhaustion — increase pool size
# postgresql.conf: max_connections = 200

# For lock contention — identify conflicting queries
psql -h {dest_host} -c "SELECT * FROM pg_stat_activity WHERE state = 'active' AND wait_event IS NOT NULL;"

# Cancel blocker if identified
psql -h {dest_host} -c "SELECT pg_cancel_backend({blocker_pid});"
```

### Step 3: Clear retry queues
```bash
# Clear retry state for specific pipeline (allows fresh starts)
# This happens automatically after retry success

# If stuck, restart the sync
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/sync/stop
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id}/sync/start
```

## Escalation
- Retries > 50/s: page Platform Lead
- Retries persisting > 1 hour: open performance ticket
- If caused by destination DB: engage DBA team

## Post-Incident
- [ ] Review retry timeout values (adjust based on observed latency)
- [ ] Add retry success rate to dashboard
- [ ] Group retry causes by error code for trend analysis
- [ ] Consider adding retry circuit breaker for non-transient errors
