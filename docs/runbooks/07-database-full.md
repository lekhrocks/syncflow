# Runbook: Database Full

> **Severity:** SEV1 — Write operations fail, data sync stops  
> **Owner:** Platform Engineering

## Symptoms
- PostgreSQL logs: `could not extend file "xxx": No space left on device`
- Write operations return 500 with `disk full` or `no space left` errors
- Snapshot and sync pipelines fail with `write failed` errors
- Alert: `PostgresDiskSpace` firing (< 10% remaining)
- Destination database unreachable for writes

## Possible Causes
1. WAL files accumulated (replication slot not consumed)
2. Audit log table grew unbounded (no retention policy)
3. Connection test snapshots filled temporary tables
4. CDC offset storage consumed too much space
5. Logging (Pod logs) filled data volume
6. Index bloat from heavy write workload

## Diagnosis

```bash
# Check disk usage
psql -h {db_host} -U syncflow -c "
  SELECT pg_size_pretty(pg_database_size('syncflow')) AS db_size,
         pg_size_pretty(sum(pg_total_relation_size(relid))) AS total_size
  FROM pg_stat_user_tables;
"

# Check largest tables
psql -h {db_host} -U syncflow -c "
  SELECT relname AS table,
         pg_size_pretty(pg_total_relation_size(relid)) AS total,
         pg_size_pretty(pg_relation_size(relid)) AS data,
         pg_size_pretty(pg_indexes_size(relid)) AS indexes
  FROM pg_catalog.pg_statio_user_tables
  ORDER BY pg_total_relation_size(relid) DESC
  LIMIT 10;
"

# Check WAL disk usage
du -sh /var/lib/postgresql/data/pg_wal/

# Check replication slots
psql -h {db_host} -U syncflow -c "
  SELECT slot_name, slot_type, active, pg_size_pretty(pg_wal_lsn_diff(
    pg_current_wal_lsn(), restart_lsn)) AS retained_wal
  FROM pg_replication_slots;
"

# Check pod logs disk usage
kubectl exec -n syncflow deploy/syncflow -- df -h /app
```

## Recovery Steps

### Step 1: Free immediate space

```bash
# Option A: Truncate old audit logs (retention: 90 days)
psql -h {db_host} -U syncflow -c "
  DELETE FROM audit_logs WHERE timestamp < NOW() - INTERVAL '90 days';
  VACUUM audit_logs;
"

# Option B: Remove unused replication slots
psql -h {db_host} -U syncflow -c "
  SELECT pg_drop_replication_slot('syncflow_slot');
"

# Option C: Reindex bloated indexes
psql -h {db_host} -U syncflow -c "REINDEX DATABASE syncflow;"

# Option D: Vacuum full (last resort — acquires exclusive lock)
psql -h {db_host} -U syncflow -c "VACUUM FULL;"
```

### Step 2: Add storage

```bash
# Kubernetes PVC expansion (if storage class supports it)
kubectl edit pvc postgres-pvc -n syncflow
# Increase spec.resources.requests.storage

# Or for AWS RDS
aws rds modify-db-instance --db-instance-identifier syncflow \
  --allocated-storage 200 --apply-immediately

# Verify: psql -c "SHOW data_directory;"
```

### Step 3: Restart affected pipelines

```bash
# Resume all failed pipelines
for pid in $(curl -u admin:$TOKEN http://syncflow.example.com/api/pipelines | jq -r '.[].id'); do
  curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/$pid/validate
  curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/pipelines/$pid/snapshot
done
```

### Step 4: Verify recovery

```bash
# Disk usage should be < 70%
psql -h {db_host} -U syncflow -c "
  SELECT pg_size_pretty(pg_database_size('syncflow')) AS db_size;
"

# Pipelines should be running
curl -u admin:$TOKEN http://syncflow.example.com/api/pipelines | jq '.[] | {name, status}'
```

## Escalation
- Space < 5%: SEV1, page Platform Lead and DBA
- Space < 10% for > 1 hour: page DBA, increase storage
- WAL disk full: replication slot issue, page DBA

## Post-Incident
- [ ] Set up automated VACUUM schedule (hourly for high-write tables)
- [ ] Add audit log retention TTL with auto-purge
- [ ] Add pg_dump based on disk usage alerts
- [ ] Configure WAL retention limits in postgresql.conf
- [ ] Add storage expansion automation
