# Runbooks

Troubleshooting guides for common failure scenarios. Full runbooks in `docs/runbooks/`.

## Pipeline Failure

**Symptoms:** Pipeline status = FAILED, events not flowing

**Diagnosis:**
```bash
# Check pipeline status
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipelines/{id}

# Check DLQ
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/dashboard/errors

# Check logs
kubectl logs -l app=syncflow --tail=100 | grep -i "pipeline.*error"
```

**Resolution:**
1. Verify source/destination connections are healthy
2. Check for schema changes in source database
3. Review DLQ for failed events
4. Restart capture: `POST /api/pipelines/{id}/capture/stop` then `start`

## CDC Lag

**Symptoms:** CDC lag > 30 seconds, events delayed

**Diagnosis:**
```bash
# Check CDC metrics
curl http://localhost:8080/api/diagnostics/connectors

# Check capture status
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipelines/{id}/capture/status
```

**Resolution:**
1. Increase `syncflow.runtime.sync.batch-size`
2. Check destination write latency
3. Verify network between source and SyncFlow
4. Consider adding Kafka transport for cross-pod distribution

## Checkpoint Corruption

**Symptoms:** Snapshot resumes from wrong position, duplicate data

**Diagnosis:**
```bash
# Check snapshot checkpoints
psql -d syncflow -c "SELECT * FROM snapshot_checkpoints WHERE pipeline_id = '{id}'"
```

**Resolution:**
1. Delete corrupted checkpoints: `DELETE FROM snapshot_checkpoints WHERE pipeline_id = '{id}'`
2. Restart snapshot from beginning
3. Verify `chunk_index` values are sequential

## Agent Offline

**Symptoms:** Agent not sending heartbeats, jobs failing

**Diagnosis:**
```bash
# Check agent status
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/agents

# Check agent logs
kubectl logs -l app=syncflow-agent --tail=100
```

**Resolution:**
1. Verify agent can reach control plane URL
2. Check agent token is valid
3. Restart agent pod
4. Drain agent before shutdown: `POST /api/agents/{id}/drain`

## Out of Memory

**Symptoms:** OOMKilled, heap exhaustion

**Diagnosis:**
```bash
# Check JVM metrics
curl http://localhost:8080/api/diagnostics/system

# Check heap usage
curl http://localhost:9090/actuator/metrics/jvm.memory.used
```

**Resolution:**
1. Increase `-Xmx` in JVM args
2. Reduce `syncflow.runtime.sync.queue-capacity`
3. Reduce `syncflow.runtime.snapshot.parallelism`
4. Check for memory leaks in connector clones

## Database Full

**Symptoms:** Write failures, Flyway migration errors

**Diagnosis:**
```bash
# Check database size
psql -d syncflow -c "SELECT pg_size_pretty(pg_database_size('syncflow'))"

# Check table sizes
psql -d syncflow -c "SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) FROM pg_tables WHERE schemaname='public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC"
```

**Resolution:**
1. Clean DLQ: `DELETE FROM dead_letter_events WHERE created_at < NOW() - INTERVAL '7 days'`
2. Clean processed events: `DELETE FROM processed_events WHERE created_at < NOW() - INTERVAL '30 days'`
3. Vacuum: `VACUUM FULL ANALYZE`
4. Add storage or archival policy

## Slow Sync

**Symptoms:** Sync latency > 60 seconds

**Diagnosis:**
```bash
# Check sync metrics
curl http://localhost:8080/api/dashboard/metrics

# Check writer performance
curl http://localhost:8080/api/diagnostics/connectors
```

**Resolution:**
1. Increase `syncflow.runtime.sync.batch-size`
2. Check destination database performance
3. Verify network latency
4. Consider connection pooling (already uses HikariCP)
5. Check for lock contention in destination

## High Retry Count

**Symptoms:** Events retrying frequently, approaching DLQ threshold

**Diagnosis:**
```bash
# Check retry metrics
curl http://localhost:8080/api/dashboard/errors

# Check specific pipeline errors
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipelines/{id}/capture/status
```

**Resolution:**
1. Identify root cause from error messages
2. Fix transient issues (network, locks)
3. Adjust `syncflow.runtime.retry.max-attempts` if needed
4. Review DLQ for permanent failures

## High DLQ Depth

**Symptoms:** DLQ growing, events not processing

**Diagnosis:**
```bash
# Check DLQ count
curl http://localhost:8080/api/dashboard/errors

# List DLQ events
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/dead-letter?pipelineId={id}
```

**Resolution:**
1. Fix root cause of failures
2. Replay resolved events: `POST /api/dead-letter/{id}/replay`
3. Purge old events: `DELETE /api/dead-letter?olderThan=7d`
4. Set up alerts for DLQ depth threshold
