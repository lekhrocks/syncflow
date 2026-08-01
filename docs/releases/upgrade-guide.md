# SyncFlow Upgrade Guide

> **Applies to:** All 0.x releases  
> **Minimum interval:** Do not skip versions — apply sequentially

---

## Upgrade Path

```
0.1.0 → (future) 0.2.0 → (future) 1.0.0
```

## Pre-Upgrade Checklist

- [ ] Review CHANGELOG.md for breaking changes
- [ ] Verify current version: `curl /actuator/info | jq '.version'`
- [ ] Verify database migration: `SELECT version FROM flyway_schema_history ORDER BY version DESC LIMIT 1`
- [ ] Take a database backup: `pg_dump -U syncflow syncflow > pre-upgrade.sql`
- [ ] Notify impacted pipeline owners
- [ ] Check compatibility matrix (Java, Kubernetes, database versions)

## Upgrade Procedure

### Helm Upgrade

```bash
# 1. Backup
./scripts/backup.sh

# 2. Update Helm repository
helm repo update

# 3. Review changes
helm diff upgrade syncflow helm/syncflow/ \
  --values prod-values.yaml

# 4. Apply upgrade
helm upgrade syncflow helm/syncflow/ \
  --values prod-values.yaml \
  --timeout 10m \
  --atomic

# 5. Verify
kubectl rollout status deployment syncflow -n syncflow-prod --timeout=300s
```

### Manual Upgrade (No Helm)

```bash
# 1. Backup
./scripts/backup.sh

# 2. Apply new manifests
kubectl apply -k k8s/overlays/prod

# 3. Wait for rollout
kubectl rollout status deployment syncflow -n syncflow-prod --timeout=300s

# 4. Verify migration
kubectl exec deploy/syncflow -n syncflow-prod -- \
  curl -s http://localhost:8080/actuator/flyway
```

## Post-Upgrade Verification

```bash
# 1. Health check
curl -sf https://syncflow.example.com/actuator/health | jq '.status'

# 2. Migration status
curl -sf https://syncflow.example.com/actuator/flyway | jq '.migrations[-1]'

# 3. API smoke test
curl -sf https://syncflow.example.com/api/health | jq '.status == "UP"'
curl -sf https://syncflow.example.com/api/connections | jq 'type == "array"'
curl -sf https://syncflow.example.com/api/dashboard/overview | jq '.pipelines.total > 0'

# 4. Agent connectivity
curl -sf https://syncflow.example.com/api/agents | jq 'length > 0'

# 5. Verify event processing
curl -sf https://syncflow.example.com/api/sync/jobs | jq '.[0].statistics'
```

## Rollback

```bash
# Helm rollback
helm rollback syncflow 1

# Manual rollback (previous manifests)
kubectl apply -f previous-deployment.yaml

# Database rollback (if migration was backward compatible)
# Restore from backup
./scripts/restore.sh <backup-timestamp>
```

## Zero-Downtime Guarantee

Upgrades are zero-downtime when:
1. RollingUpdate is used (`maxUnavailable: 0`)
2. PodDisruptionBudget is configured (`minAvailable: 1`)
3. Migrations are backward compatible (no DROP, RENAME, or TYPE changes)
4. Readiness probe passes before traffic is routed to new pods

Estimated downtime during upgrade: **0 seconds** (rolling update)
