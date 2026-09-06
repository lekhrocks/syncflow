# Observability

## Metrics (Micrometer + Prometheus)

All metrics are exposed at `/actuator/prometheus`.

### Application Metrics

| Metric | Type | Tags |
|--------|------|------|
| `syncflow.snapshot.duration` | Timer | `pipeline` |
| `syncflow.snapshot.rows` | Counter | `pipeline` |
| `syncflow.snapshot.errors` | Counter | `pipeline` |
| `syncflow.cdc.events` | Counter | `pipeline`, `table` |
| `syncflow.cdc.lag` | Gauge | `pipeline` |
| `syncflow.sync.duration` | Timer | `pipeline` |
| `syncflow.sync.events` | Counter | `pipeline` |
| `syncflow.sync.errors` | Counter | `pipeline` |
| `syncflow.dlq.count` | Gauge | `pipeline` |

### Infrastructure Metrics

- JVM metrics (heap, GC, threads)
- HikariCP metrics (connections, pool usage)
- Tomcat metrics (requests, threads)
- Spring MVC metrics

### Custom Tags

All metrics include `application: syncflow` tag (configurable via `management.metrics.tags.application`).

## Distributed Tracing (OpenTelemetry)

Traces are exported to OTLP endpoint:

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

### Trace Propagation

- MDC integration: `traceId`, `correlationId`, `pipelineId` in all log lines
- HTTP header propagation for inter-service calls
- Debezium engine traces

### Log Pattern

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] [%X{correlationId}] [%X{pipelineId}] %-5level %logger{36} - %msg%n
```

## Dashboards (Grafana)

Pre-configured dashboards in `docker/grafana/`:

| Dashboard | Panels |
|-----------|--------|
| Overview | Pipeline status, connection health, active captures |
| CDC | Event throughput, lag, error rate |
| Snapshot | Progress, duration, rows processed |
| System | JVM, CPU, memory, connections |

## Health Checks

```bash
# Basic health
GET /api/health

# Detailed with connector status
GET /api/health?show-details=always

# Kubernetes probes
GET /actuator/health/liveness
GET /actuator/health/readiness
```

## Diagnostics

| Endpoint | Description |
|----------|-------------|
| `GET /api/diagnostics/system` | JVM/OS/Java info |
| `GET /api/diagnostics/connectors` | Connector health + latency |
| `GET /api/diagnostics/pipelines` | Pipeline status + metrics |
| `GET /api/diagnostics/executions` | Recent execution history |

## Alerting

Prometheus alerting rules in `k8s/base/prometheusrule.yaml`:

- High error rate (> 1% of events)
- CDC lag exceeding threshold (> 30s)
- Snapshot duration exceeding SLA (> 1h)
- DLQ depth growing
- Agent offline
- Memory usage > 80%

## SSE (Server-Sent Events)

Live progress streaming for snapshots:

```
GET /api/snapshots/{id}/events
```

Events:
- `snapshot-status` — Progress updates (batches, rows, percentage)
- `snapshot-complete` — Terminal state (COMPLETED/CANCELLED/FAILED)
