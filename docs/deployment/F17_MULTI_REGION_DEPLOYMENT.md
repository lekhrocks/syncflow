# F17 Multi-Region / Geo-Replication Deployment Guide

## Overview

This guide walks through deploying SyncFlow with multi-region geo-replication for HA and disaster recovery.

**Topology:** Single-primary, multi-replica (read-only) with automatic failover.

**RTO:** 1–2 minutes (automatic failover via RegionalFailoverManager)  
**RPO:** < 30 seconds (continuous logical replication)

---

## Architecture

```
┌─────────────────────────────┐              ┌──────────────────────────┐
│   US-East (Primary)         │              │  EU-West (Replica)       │
│  ┌─────────────────────┐    │              │ ┌──────────────────────┐ │
│  │ Syncflow Pods (3)   │    │              │ │ Syncflow Pods (2)    │ │
│  │ ├─ Capture         │    │              │ │ └──────────────────┬─┘ │
│  │ ├─ Sync            │    │              │ └────────────────────┼──┘ │
│  │ └─ Consistency Chk │    │              │                     │     │
│  └────────┬───────────┘    │              │                     │     │
│           │                │              │                     │     │
│  ┌────────▼──────────┐     │ Pub/Sub      │ ┌──────────────────▼──┐  │
│  │ PostgreSQL Primary│◄────┼─────────────►│ │ PostgreSQL Replica │  │
│  │                   │     │ (Logical)    │ │ (Read-Only)        │  │
│  │ ✓ CDC Active      │     │              │ │ (Writable on fail) │  │
│  │ ✓ Writes OK       │     │              │ │                    │  │
│  └───────────────────┘     │              │ └────────────────────┘  │
│                            │              │                         │
└─────────────────────────────┘              └──────────────────────────┘

On Primary Failure:
1. RegionalFailoverManager detects 3x failed health checks
2. Selects best standby (EU-West)
3. Waits for replication lag < 1s
4. Drops subscription on EU-West (makes writable)
5. Updates RegionalDataSourceFactory to route writes to EU-West
6. All pods follow new primary automatically
```

---

## Prerequisites

### PostgreSQL Requirements (All Regions)

1. **Logical replication enabled** (requires `wal_level = logical`)
   - AWS RDS: Parameter group setting
   - Self-managed: Edit `postgresql.conf`, restart server
   
   ```bash
   wal_level = logical
   max_wal_senders = 5
   max_replication_slots = 5
   ```

2. **Network connectivity** between all PostgreSQL instances

3. **Database schema initialized** (V1–V17 migrations applied on primary)

### Kubernetes Requirements

- 3 clusters (US-East, EU-West, AP-Southeast) or 3 namespaces in single cluster
- Ingress or service mesh for cross-cluster communication
- ConfigMaps for region-specific configuration

---

## Step 1: Enable Logical Replication (Primary Only)

### AWS RDS
1. Go to Parameter Groups → Create new
2. Add parameters:
   ```
   wal_level = logical
   max_wal_senders = 5
   max_replication_slots = 5
   ```
3. Attach to RDS instance (requires restart)
4. Restart: RDS Console → DB Instances → Reboot

### Self-Managed PostgreSQL
```bash
# On primary server
sudo vi /etc/postgresql/16/main/postgresql.conf
# Add:
wal_level = logical
max_wal_senders = 5
max_replication_slots = 5

# Restart
sudo systemctl restart postgresql
```

**Verify:**
```sql
SHOW wal_level;  -- should return 'logical'
SHOW max_wal_senders;  -- should return >= 5
```

---

## Step 2: Create Publication on Primary

Run V17 migration on primary database:
```bash
# Primary (US-East) — migrations run automatically on first pod startup
kubectl rollout restart deployment/syncflow -n syncflow-us-east

# Check migration succeeded
kubectl logs -f <pod-name> -n syncflow-us-east | grep "V17"
```

**Manual verification** (if needed):
```sql
-- On primary database (US-East)
SELECT * FROM pg_publication;
-- Should show: syncflow_pub

SELECT * FROM pg_replication_slots;
-- Should show: syncflow_eu_west_slot, syncflow_ap_southeast_slot
```

---

## Step 3: Create Subscriptions on Replicas

### EU-West Replica
```bash
# Connect to EU-West database
psql -h eu-west-db.example.com -U syncflow -d syncflow

# Create subscription (subscribes to US-East primary)
CREATE SUBSCRIPTION syncflow_sub
  CONNECTION 'postgresql://syncflow:PASSWORD@us-east-db.example.com:5432/syncflow'
  PUBLICATION syncflow_pub
  WITH (copy_data = true, enabled = true);

-- Wait for initial data copy (may take several minutes for large databases)
SELECT subscription_name, substate, substate_time 
FROM pg_subscription;

-- Should show: f (finished) when ready
```

### AP-Southeast Replica
```bash
# Same process for AP-Southeast
psql -h ap-southeast-db.example.com -U syncflow -d syncflow

CREATE SUBSCRIPTION syncflow_sub
  CONNECTION 'postgresql://syncflow:PASSWORD@us-east-db.example.com:5432/syncflow'
  PUBLICATION syncflow_pub
  WITH (copy_data = true, enabled = true);

-- Wait for sync
SELECT subscription_name, substate, substate_time 
FROM pg_subscription;
```

**Verify replication is working:**
```sql
-- On primary
SELECT slot_name, slot_type, active, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag_bytes
FROM pg_replication_slots;
-- Should show active=true, lag_bytes = small number

-- On replica
SELECT subscription_name, substate, latest_end_lsn
FROM pg_subscription;
-- Should show recent timestamps
```

---

## Step 4: Deploy Regional Configuration

Create region-specific ConfigMaps:

### US-East Primary
```bash
kubectl create configmap syncflow-regional-config \
  --from-file=regional-config=docs/deployment/regional-config-us-east.yml \
  -n syncflow-us-east

kubectl patch deployment syncflow -n syncflow-us-east -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"syncflow","volumeMounts":[{"name":"regional-config","mountPath":"/etc/syncflow/regional"}]}]}}}}'
```

**regional-config-us-east.yml:**
```yaml
syncflow:
  region:
    local-region: us-east-1
    primary-region: us-east-1
    replication-enabled: true
    auto-failover: true
    health-check-interval: 30s
    failover-threshold: 3
    max-replication-lag-ms: 5000
    regions:
      us-east-1:
        connection-string: "postgresql://syncflow:password@us-east-db:5432/syncflow"
        write-enabled: true
      eu-west-1:
        connection-string: "postgresql://syncflow:password@eu-west-db:5432/syncflow"
        write-enabled: false
      ap-southeast-1:
        connection-string: "postgresql://syncflow:password@ap-southeast-db:5432/syncflow"
        write-enabled: false
```

### EU-West Replica
```yaml
syncflow:
  region:
    local-region: eu-west-1
    primary-region: us-east-1
    replication-enabled: true
    auto-failover: true
    regions:
      us-east-1:
        connection-string: "postgresql://syncflow:password@us-east-db:5432/syncflow"
        write-enabled: true
      eu-west-1:
        connection-string: "postgresql://syncflow:password@eu-west-db:5432/syncflow"
        write-enabled: false
      ap-southeast-1:
        connection-string: "postgresql://syncflow:password@ap-southeast-db:5432/syncflow"
        write-enabled: false
```

---

## Step 5: Deploy SyncFlow with Regional Support

```bash
# Deploy to all regions
for region in us-east eu-west ap-southeast; do
  helm upgrade syncflow syncflow/helm/syncflow \
    -n syncflow-${region} \
    -f docs/deployment/regional-config-${region}.yml \
    --set region.local-region=${region}-1 \
    --set region.primary-region=us-east-1 \
    --set region.replication-enabled=true \
    --set region.auto-failover=true
done

# Wait for rollout
kubectl rollout status deployment/syncflow -n syncflow-us-east --timeout=5m
kubectl rollout status deployment/syncflow -n syncflow-eu-west --timeout=5m
kubectl rollout status deployment/syncflow -n syncflow-ap-southeast --timeout=5m
```

---

## Step 6: Verify Regional Setup

### Check datasource health
```bash
# US-East pod should report primary=healthy, replicas=healthy
kubectl port-forward -n syncflow-us-east svc/syncflow 8080:8080
curl http://localhost:8080/actuator/health/regional | jq .
```

**Expected response:**
```json
{
  "status": "UP",
  "details": {
    "multi_region_enabled": true,
    "primary_region": "us-east-1",
    "primary_healthy": true,
    "replicas": {
      "eu-west-1": true,
      "ap-southeast-1": true
    },
    "consistency": "[us-east-1 PRIMARY] events=150000 [eu-west-1 OK] lag=12 [ap-southeast-1 OK] lag=8"
  }
}
```

### Check replication lag
```bash
# On primary database
SELECT slot_name, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) / 1024 / 1024 AS lag_mb
FROM pg_replication_slots;

# Should show < 10 MB lag under normal load
```

### Check capture runs in correct region
```bash
# Create test pipeline
curl -X POST http://localhost:8080/api/pipelines \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-multi-region",
    "regionStrategy": "PRIMARY_STANDBY",
    "preferredRegion": "us-east-1"
  }'

# Check logs: should see capture starting in US-East
kubectl logs -f -n syncflow-us-east deployment/syncflow | grep "capture.*start"

# In EU-West: should be skipped
kubectl logs -f -n syncflow-eu-west deployment/syncflow | grep "capture.*skipping"
```

---

## Step 7: Test Failover (Staging Only)

### Simulate primary failure
```bash
# Break primary database connection
# (Option A: Kill primary pod in test cluster)
# (Option B: Block network traffic)
# (Option C: Stop PostgreSQL service on primary)

# Monitor failover
kubectl logs -f -n syncflow-eu-west deployment/syncflow | grep -i "failover\|promote\|failover"
```

**Expected sequence (< 1 minute):**
1. Health checks fail 3x (health check interval = 30s, so ~90 seconds)
2. RegionalFailoverManager detects failure
3. Selects best standby (EU-West, lowest lag)
4. Waits for replication lag < 1s
5. Drops subscription on EU-West
6. EU-West becomes primary
7. Writes resume from EU-West

### Verify traffic routed to new primary
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health/regional | jq '.details | {primary_region, primary_healthy}'

# Should show: "primary_region": "eu-west-1"
```

---

## Step 8: Configure Pipeline Region Strategies

### Create pipeline for specific region (compliance)
```bash
curl -X POST http://localhost:8080/api/pipelines \
  -H "Content-Type: application/json" \
  -d '{
    "name": "eu-data-only",
    "source": {...},
    "destination": {...},
    "regionStrategy": "LOCAL_ONLY",
    "preferredRegion": "eu-west-1"
  }'
```

### Create pipeline for HA (active-active)
```bash
curl -X POST http://localhost:8080/api/pipelines \
  -H "Content-Type: application/json" \
  -d '{
    "name": "critical-sync",
    "regionStrategy": "ACTIVE_ACTIVE"
  }'
# Runs in all regions simultaneously
```

### Create pipeline for primary-only (cost optimization)
```bash
curl -X POST http://localhost:8080/api/pipelines \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cost-optimized",
    "regionStrategy": "PRIMARY_STANDBY"
  }'
# Runs only in US-East (primary); fails over to EU-West on disaster
```

---

## Monitoring & Alerting

### Metrics Exposed

```
# Regional replication lag (milliseconds)
regional.consistency.event_lag{primary="us-east-1", replica="eu-west-1"}

# Data divergence (# of events)
regional.consistency.event_lag{...}

# Failover count
regional.failover.count{region="eu-west-1"}

# Health status
regional.consistency.status{...} → 0=OK, 1=DIVERGED
```

### Alerts to Configure

1. **Replication lag > 5 seconds**
   ```
   regional.consistency.event_lag > 5000
   ```

2. **Data divergence > 1000 events**
   ```
   regional.consistency.event_lag > 1000
   ```

3. **Primary unhealthy for > 30 seconds**
   ```
   regional.consistency.status == 1
   ```

4. **Failover triggered**
   ```
   increase(regional.failover.count[5m]) > 0
   ```

---

## Troubleshooting

### Replication lagging
```sql
-- Check subscription status on replica
SELECT * FROM pg_subscription_rel;

-- Check slot status on primary
SELECT slot_name, active, restart_lsn 
FROM pg_replication_slots WHERE slot_name = 'syncflow_eu_west_slot';

-- Check network latency
ping eu-west-db.example.com
```

### Failover not triggering
```
# Check RegionalFailoverManager logs
kubectl logs -f deployment/syncflow -n syncflow-us-east | grep -i "regional\|failover"

# Check auto-failover is enabled
curl http://localhost:8080/actuator/configprops | jq '.propertySources[] | select(.name | contains("regional"))'
```

### Data divergence detected
```sql
-- Compare event counts
SELECT COUNT(*) FROM processed_events;

-- Check pending DLQ events
SELECT COUNT(*) FROM dlq_events WHERE status = 'PENDING';

-- Replay DLQ
curl -X POST http://localhost:8080/api/dlq/replay
```

---

## Rollback to Single-Region

If multi-region needs to be disabled:

```bash
# Update config
kubectl patch configmap syncflow-regional-config -n syncflow-us-east -p \
  '{"data":{"regional-config":"sync...replication-enabled: false"}}'

# Restart pods
kubectl rollout restart deployment/syncflow -n syncflow-us-east

# All operations return to US-East primary only
```

---

## RTO/RPO Targets

| Scenario | Target | Actual |
|----------|--------|--------|
| Pod failure | < 30s | ~ 10s (K8s restart) |
| Single node failure | < 1m | ~ 30s (K8s reschedule) |
| Primary region failure | < 2min | ~ 90s (3x health check intervals + promotion) |
| Primary + standby failure | TBD | Requires manual intervention |

---

## Next Steps

1. **Helm chart updates**: Add regional deployment profiles
2. **DNS failover**: Set up Route53 / Cloud DNS for automatic routing
3. **Persistent ephemeral state**: Archive eventQueues on region boundary
4. **Monitoring dashboard**: Create Grafana dashboard for regional status

See `docs/operations/DISASTER_RECOVERY.md` for recovery procedures.
