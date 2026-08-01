# Runbook: CDC Lag

> **Severity:** SEV2 — Event processing delayed  
> **Owner:** Platform Engineering  

## Symptoms
- `CDCLagHigh` alert firing (`syncflow_cdc_events - syncflow_sync_events_processed > 10000`)
- Sync dashboards show increasing gap between "Events Captured" and "Events Processed"
- `GET /api/pipelines/{id}/capture/status` shows large queue
- Destination updates lag source by > 5 minutes

## Possible Causes
1. Sync consumer slower than CDC producer (CPU/memory constraint)
2. Destination write bottleneck (slow disk, lock contention)
3. Network bandwidth saturation between agent and destination
4. Long-running transactions in source database blocking WAL
5. Sync processor crashed (no restart)

## Diagnosis

```bash
# Check current lag
curl -u admin:$TOKEN http://syncflow.example.com/api/dashboard/metrics | jq '{queue_depth: .queue, lag: .lag}'

# Check agent health
curl -u admin:$TOKEN http://syncflow.example.com/api/agents | jq '.[] | {hostname, status, cpu, runningJobs}'

# Check sync job stats
curl -u admin:$TOKEN http://syncflow.example.com/api/sync/jobs/{id} | jq '.statistics'

# Check resource utilization
kubectl top pod -n syncflow --containers

# Check destination write latency
psql -h {dest_host} -c "
  SELECT pid, state, wait_event, wait_event_type, query 
  FROM pg_stat_activity 
  WHERE state = 'active' AND duration > interval '1 second'
"
```

## Recovery Steps

### Step 1: Scale horizontally (immediate)
```bash
# Increase sync replicas
kubectl scale deployment syncflow -n syncflow --replicas=5

# Or via HPA (automatic)
kubectl edit hpa syncflow -n syncflow  # set maxReplicas=20
```

### Step 2: Check destination write throughput
```bash
# PostgreSQL: check for long-running queries
psql -h {dest_host} -c "SELECT pid, NOW()-query_start AS duration, query FROM pg_stat_activity WHERE state='active' AND duration > '1 second' ORDER BY duration DESC"

# Cancel blocking queries if needed
psql -h {dest_host} -c "SELECT pg_cancel_backend(pid) FROM pg_stat_activity WHERE pid = {pid}"
```

### Step 3: Optimize sync settings
```bash
# Increase batch size (less frequent writes, larger transactions)
# via PUT /api/pipelines/{id} with batchSize=5000
curl -X PUT -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id} \
  -d '{"batchSize": 5000, ...}'

# Or via UI: Pipeline → Settings → Batch Size
```

### Step 4: Restart sync processor (if needed)
```bash
# Rolling restart of all control plane pods
kubectl rollout restart deployment syncflow -n syncflow

# Watch progress
kubectl rollout status deployment syncflow -n syncflow
```

### Step 5: Verify lag is decreasing
```bash
# Monitor lag for 5 minutes
watch -n 10 "curl -s -u admin:\$TOKEN http://syncflow.example.com/api/dashboard/metrics | jq '.lag'"
```

## Escalation
- Lag > 1 hour for 30+ minutes: page **Platform Lead**
- Lag > 6 hours: open SEV1, consider halting new pipelines to prevent unbounded growth
- Repeated lag spikes: open performance review ticket

## Post-Incident
- [ ] Review batch size and parallelism settings
- [ ] Check for slow queries on destination database
- [ ] Validate destination has sufficient IOPS provisioned
- [ ] Add alert if lag > 30 minutes for 15+ minutes
