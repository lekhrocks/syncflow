# Runbook: Slow Synchronization

> **Severity:** SEV3 — Throughput degradation, destination lags  
> **Owner:** Platform Engineering

## Symptoms
- Sync dashboards show rows processed decreasing over time
- `GET /api/sync/jobs/{id}` shows high latency per batch
- Destination updates take minutes instead of seconds
- No errors or failures — just slower than normal
- High CPU or I/O wait on destination database

## Possible Causes
1. Destination database CPU/memory pressure
2. Large batch of sequential UPDATEs (no batch optimization)
3. Index rebuild on destination table
4. Network bandwidth saturation between agent and destination
5. Transformation pipeline CPU-bound (heavy regex, expression evaluation)
6. Destination table lock contention from concurrent writes

## Diagnosis

```bash
# Check sync statistics
curl -u admin:$TOKEN http://syncflow.example.com/api/sync/jobs/{id} | jq '.statistics'

# Check agent resource utilization
curl -u admin:$TOKEN http://syncflow.example.com/api/agents/{id}/metrics | jq .

# Check destination database performance
psql -h {dest_host} -c "
  SELECT pg_stat_activity.datname,
         pg_stat_activity.query,
         pg_stat_activity.state,
         pg_stat_activity.wait_event,
         pg_stat_activity.wait_event_type
  FROM pg_stat_activity
  WHERE state = 'active'
  ORDER BY query_start DESC;
"

# Check for lock contention
psql -h {dest_host} -c "
  SELECT blocked_locks.pid AS blocked_pid,
         blocked_activity.query AS blocked_query,
         blocking_locks.pid AS blocking_pid,
         blocking_activity.query AS blocking_query
  FROM pg_locks blocked_locks
  JOIN pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
  JOIN pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
    AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
    AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
    AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
    AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
    AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
    AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
    AND blocking_locks.pid != blocked_locks.pid
  WHERE NOT blocked_locks.granted;
"
```

## Recovery Steps

### Step 1: Optimize batch size
```bash
# Increase batch size to reduce per-batch overhead
curl -X PUT -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id} \
  -d '{"batchSize": 5000, ...}'

# Try batchSize settings: 1000 (default), 5000, 10000, 25000
# PostgreSQL sweet spot: 5000-10000 (depends on row size)
```

### Step 2: Check destination indexes
```bash
# Identify unused or bloated indexes
psql -h {dest_host} -c "
  SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
  FROM pg_stat_user_indexes
  WHERE idx_scan = 0
  ORDER BY idx_scan ASC;
"

# Remove unused indexes that slow writes
psql -h {dest_host} -c "DROP INDEX CONCURRENTLY IF EXISTS {unused_index};"

# Reindex bloated indexes
psql -h {dest_host} -c "REINDEX INDEX CONCURRENTLY {index_name};"
```

### Step 3: Scale sync workers
```bash
# Increase sync replicas
kubectl scale deployment syncflow -n syncflow --replicas=5

# Or enable HPA if not already
kubectl autoscale deployment syncflow -n syncflow --cpu-percent=60 --min=2 --max=10
```

### Step 4: Profile transformation pipeline
```bash
# Use AI Copilot to analyze performance
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/ai/performance \
  -d '{"pipelineId": "{id}"}' | jq '.message'

# Check for heavy transformations in pipeline
curl -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id} | jq '.tableMappings[].columnMappings[].transformations'
```

## Escalation
- Sync slower than source write rate for > 30 minutes: page Platform Lead
- Latency > 10x normal for > 1 hour: open performance ticket
- If caused by destination DB: engage DBA team

## Post-Incident
- [ ] Add sync throughput benchmark to CI (prevent regression)
- [ ] Review batch size auto-tuning logic
- [ ] Add destination DB performance monitoring in Grafana
- [ ] Document optimal batch sizes for common database combinations
