# SyncFlow Operations Runbook

## Incident Response

### Pipeline Failure
1. Check `/api/dashboard/errors` for failed snapshots or sync jobs
2. Inspect `/api/sync/jobs/{id}/statistics` for retry/failure counts  
3. View `/api/dlq?pipelineId={id}` for dead-lettered events
4. Use `/api/ai/root-cause` for automated diagnosis

### Agent Offline
1. Check agent heartbeat: `kubectl get pods -n syncflow -l app=syncflow-agent`
2. Verify network connectivity between agent and control plane
3. Restart agent: `kubectl rollout restart deployment syncflow-agent`
4. If unreachable >5min, check `/api/agents/{id}` status

### CDC Lag
1. Compare CDC events produced vs sync events processed: `/api/pipelines/{id}/capture/status`
2. Check `syncflow_cdc_events_total` vs `syncflow_sync_events_processed_total`
3. Increase sync batch size or add worker replicas

### Database Connection Lost
1. Verify connection health: `/api/connections/{id}/health`
2. Check credentials haven't rotated via External Secrets
3. Retest: POST `/api/connections/test` 

## Maintenance Procedures

### Backup
```bash
./scripts/backup.sh
kubectl exec deploy/syncflow -- pg_dump -U syncflow syncflow > backup.sql
```

### Restore
```bash
./scripts/restore.sh <backup-timestamp>
```

### Upgrade
```bash
helm upgrade syncflow helm/syncflow -f values-prod.yaml
kubectl rollout status deploy/syncflow --timeout=300s
```

### Rollback
```bash
helm rollback syncflow 1
./argocd/rollback.sh
```
