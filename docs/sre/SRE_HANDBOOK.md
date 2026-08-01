# SyncFlow SRE Handbook

## Service Level Objectives

| SLI | SLO | Measurement |
|-----|-----|-------------|
| API Availability | 99.9% | Rate(2xx+3xx) / Total requests, 30d rolling |
| Pipeline Success | 99.5% | Successful pipeline ops / Total ops, 30d rolling |
| CDC Event Processing | 99.9% | Events consumed within 60s of capture |
| Snapshot Completion | 99% | Snapshots completing without error |
| Agent Connectivity | 99.9% | Agents reporting heartbeat within 60s |

## Error Budgets

Monthly error budget at 99.9% availability: 43 minutes of downtime.
Track via `(1 - error_rate / 0.001)` Prometheus gauge.

## Capacity Planning

Each control plane replica handles:
- 500+ pipeline definitions
- 1000+ connections  
- 50 concurrent snapshots
- 100k events/second throughput (with KEDA autoscaling)

Scale triggers:
- Queue depth > 100 → add replicas
- CPU > 60% → add replicas  
- Memory > 75% → add replicas

## Incident Severity Levels

| Level | Response Time | Example |
|-------|---------------|---------|
| SEV1 | 15min | Production pipeline failure, data loss |
| SEV2 | 30min | CDC lag > 10min, multiple agent offline |
| SEV3 | 2hr | Single agent offline, slow queries |
| SEV4 | 24hr | UI cosmetic issue, documentation update |
