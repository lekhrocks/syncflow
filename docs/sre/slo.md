# SyncFlow SLOs & Error Budgets

> **Version:** 1.0  
> **Owner:** Platform Engineering  
> **Review Cycle:** Quarterly  

---

## Service Level Objectives (SLOs)

### API Availability

| Tier | SLO | Measurement | Window |
|------|-----|-------------|--------|
| Control Plane API | **99.95%** | `sum(rate(http_requests_total{code=~"2..\|3.."}[30d])) / sum(rate(http_requests_total[30d]))` | 30d rolling |
| Agent Heartbeat | **99.9%** | Agents reporting heartbeat within 60s window | 7d rolling |
| GraphQL API | **99.9%** | GraphQL query success rate | 30d rolling |

**Error budget (99.95%):** 21 minutes downtime per month / 4.3 hours per year.

### Latency

| Metric | SLO | Measurement | Window |
|--------|-----|-------------|--------|
| REST API P95 | **< 250ms** | `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))` | 5m |
| REST API P99 | **< 1s** | `histogram_quantile(0.99, ...)` | 5m |
| Metadata Discovery | **< 2s** | Per-request metadata fetch | 5m |
| AI Copilot Response | **< 5s** | End-to-end LLM response time | 5m |
| Dashboard Load | **< 1s** | `/api/dashboard/overview` response | 5m |

### Error Rate

| Category | SLO | Measurement |
|----------|-----|-------------|
| HTTP 5xx Rate | **< 0.1%** | `rate(http_requests_total{code=~"5.."}[5m]) / rate(http_requests_total[5m])` |
| Pipeline Operation Error | **< 0.1%** | `rate(syncflow_pipeline_operations_total{status="error"}[5m]) / rate(syncflow_pipeline_operations_total[5m])` |
| Sync Event Failure | **< 0.5%** | `sync_sync_events_failed_total / sync_sync_events_processed_total` |

### Pipeline & Data

| Metric | SLO | Measurement |
|--------|-----|-------------|
| Snapshot Success Rate | **99.99%** | Completed snapshots / total snapshots |
| CDC Event Capture Lag | **< 10s** | Time from DB commit to CDCEvent publication (p99) |
| Sync Processing Lag | **< 60s** | Time from CDCEvent to destination write (p99) |
| CDC Recovery Time | **< 30s** | Time to resume CDC after connector restart |
| Event Delivery Rate | **99.99%** | Events delivered / events captured (30d) |

### Disaster Recovery

| Metric | Target | Definition |
|--------|--------|------------|
| Recovery Time Objective (RTO) | **< 5 min** | Time to restore service after region failure |
| Recovery Point Objective (RPO) | **< 30s** | Maximum data loss after failure (CDC offset + checkpoint) |
| Backup RTO (database) | **< 30 min** | Time to restore PostgreSQL from backup |
| Backup RPO (database) | **< 4 hours** | Maximum data loss from latest backup |

---

## Error Budgets

```
Monthly error budget at 99.95%:     21 minutes
Quarterly error budget at 99.95%:   63 minutes
Yearly error budget at 99.95%:      4.3 hours
```

### Budget Consumption

```promql
# Error budget remaining (percentage)
1 - (sum(rate(http_requests_total{code=~"5.."}[30d])) / sum(rate(http_requests_total[30d])) / 0.001)
```

| Threshold | Action |
|-----------|--------|
| **> 50% remaining** | Normal operations. Deployments proceed. |
| **20–50% remaining** | Caution. Deployments require monitoring approval. |
| **< 20% remaining** | Burn rate alert. Rollbacks frozen. Incident review required. |
| **0% (exhausted)** | Emergency. All non-critical changes halted. SRE on-call engaged. |

### Burn Rate Alerts

| Alert | Condition | Response |
|-------|-----------|----------|
| **Burn Rate 5m** | Consuming 100% budget in < 2h | Page SRE |
| **Burn Rate 30m** | Consuming 100% budget in < 6h | Page SRE |
| **Burn Rate 6h** | Consuming 100% budget in < 3d | Notify team |

---

## SLI Definitions

### API Availability SLI

```
SLI = successful_requests / total_requests
successful_requests = HTTP 200, 201, 204 (not 4xx, 5xx)
window = 30d rolling
```

### Latency SLI

```
SLI = count of requests < threshold / total requests
threshold = 250ms for P95, 1000ms for P99
measured from request ingress to response egress
```

### Snapshot Success SLI

```
SLI = snapshots_completed / (snapshots_completed + snapshots_failed)
A snapshot is considered failed if it terminates with status FAILED
Timeouts (> 2h) and cancellations are excluded
```

### CDC Delivery SLI

```
SLI = events_delivered / events_captured
events_delivered = count of events written to destination
events_captured = count of events produced by CDC connector
window = 30d rolling
Duplicate events and DLQ'd events are excluded
```

---

## Monitoring Queries

### SLO Burn Rate (5m window)

```promql
sum(rate(http_requests_total{code=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))
  / (1 - 0.9995)
```

### P95 Latency

```promql
histogram_quantile(0.95,
  sum(rate(http_request_duration_seconds_bucket[5m])) by (le)
)
```

### Error Budget Remaining

```promql
(
  1 - (
    sum(rate(http_requests_total{code=~"5.."}[30d])) /
    sum(rate(http_requests_total[30d]))
  ) / 0.001
) * 100
```

### Snapshot Success Rate

```promql
sum(syncflow_snapshot_completed_total) / (
  sum(syncflow_snapshot_completed_total) + sum(syncflow_snapshot_failed_total)
) * 100
```

---

## Related Documents

| Document | Location |
|----------|----------|
| Runbook | `docs/operations/RUNBOOK.md` |
| Disaster Recovery | `docs/operations/DISASTER_RECOVERY.md` |
| SRE Handbook | `docs/sre/SRE_HANDBOOK.md` |
| Grafana Dashboard | `grafana/dashboards/syncflow-slos.json` |
| Prometheus Rules | `k8s/base/prometheusrule.yaml` |
