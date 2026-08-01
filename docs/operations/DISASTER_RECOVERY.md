# Disaster Recovery Guide

## Recovery Objectives

- **RPO**: 4 hours (backup interval)
- **RTO**: 30 minutes (cross-region failover)
- **RTO Local**: 5 minutes (pod restart)

## Backup Strategy

| Component | Method | Frequency | Retention |
|-----------|--------|-----------|-----------|
| Database | pg_dump | Every 4 hours | 30 days |
| ConfigMaps | kubectl get -o yaml | Hourly | 90 days |
| Secrets | External Secrets Operator | On-demand | N/A |
| Pipeline definitions | API export | Daily | Forever |
| Audit logs | Immutable store | Continuous | Per policy |

## Recovery Scenarios

### Single Pod Failure
```bash
kubectl delete pod -n syncflow -l app.kubernetes.io/name=syncflow
```
Self-healing: PodDisruptionBudget ensures minAvailable=1.

### Node Failure
Kubernetes reschedules pods on remaining nodes.
TopologySpreadConstraints ensures zone distribution.

### Region Failure
```bash
kubectl config use-context <dr-region>
./scripts/disaster-recovery.sh failover
```
DNS failover via Global Load Balancer (Route53 / Cloud DNS).

### Full Cluster Failure
1. Restore PostgreSQL from S3 backup
2. Apply ConfigMaps from backup
3. Deploy Helm chart
4. Validate health: `./scripts/self-heal.sh`
5. Restore audit logs from backup
