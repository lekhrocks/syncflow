# ADR-007: Why OpenTelemetry?

## Status: Accepted

## Context
SyncFlow is a distributed system with a control plane API, database connectors, CDC engines, and workflow schedulers spread across multiple threads, virtual threads, and potentially separate processes. Debugging performance issues or tracing events across the pipeline requires end-to-end tracing and structured logging with correlation IDs.

## Decision
Use OpenTelemetry (OTel) for observability. Micrometer for metrics. Logback MDC for structured logging.

## Rationale
- **End-to-end tracing**: A single trace ID spans REST request → pipeline execution → snapshot batch → CDC event → sync processing. Exported via OTLP to Tempo.
- **Vendor neutrality**: The OTLP exporter can target Jaeger, Zipkin, Datadog, New Relic, or Grafana Tempo with no code changes.
- **MDC integration**: `traceId`, `correlationId`, `pipelineId`, and `connectionId` are pushed into Logback's MDC. JSON log output includes all fields — no regex parsing needed for filtering in Grafana/Loki.
- **Micrometer bridge**: Micrometer metrics (`syncflow.cdc.events`, `syncflow.snapshot.rows`) integrate with the OTel metrics pipeline via the OTel metrics exporter.

## Consequences
- All REST endpoints automatically generate trace spans (via Spring Boot's OTel auto-configuration).
- Custom spans added in `SnapshotExecutor`, `SyncOrchestrator`, and `WorkflowScheduler`.
- Logs can be filtered by `{traceId}` or `{pipelineId}` across services — no more grep.

## Links
- `application.yml`: `management.otlp.tracing.endpoint: http://localhost:4318/v1/traces`
- `logback-spring.xml`: `[%X{traceId}] [%X{correlationId}] [%X{pipelineId}]` in MDC
- Prometheus rules: `k8s/base/prometheusrule.yaml`
