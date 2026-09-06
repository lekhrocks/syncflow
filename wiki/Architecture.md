# Architecture

## Module Structure

```
syncflow-common        Shared primitives, tenant context, exceptions
syncflow-core          Domain models, SPI interfaces, pipeline/snapshot/CDC logic
syncflow-connectors    Concrete connectors (JDBC, Debezium, MongoDB, writers)
syncflow-persistence   JPA entities, Spring Data repos, Flyway migrations
syncflow-api           REST controllers, orchestration, runtime state
syncflow-security      Security configuration, agent token filter
syncflow-monitoring    Micrometer metrics, OpenTelemetry integration
syncflow-plugin-api    Standalone SPI for third-party connector plugins
syncflow-agent         Standalone agent for distributed execution
```

## Data Flow

```
Pipeline Design (DDL + column mapping)
        |
        v
+-------------------+     +-------------------+
| SnapshotExecutor  |     | Source Connector  |
| (batch read +     |<--->| (keyset/offset    |
|  transform +      |     |  pagination)      |
|  write)           |     +--------+----------+
+--------+----------+              |
         |                         v
         |               CDC Events (WAL/binlog)
         v
+-------------------+     +-------------------+
| CaptureLifecycle  |---->| Debezium Engine   |
| (start/stop/      |     | (streaming)       |
|  pause/resume)    |     +--------+----------+
+--------+----------+              |
         |                         v
         v              +-------------------+
+-------------------+   | SyncOrchestrator  |
| DestinationRouter |<--| (queue + process  |
| (batched writes)  |   |  + route + DLQ)   |
+-------------------+   +-------------------+
```

## Design Decisions

See [[Architecture-Decisions]] for the full ADR index. Key decisions:

- **Hexagonal Architecture** — Core has zero framework dependencies; connectors implement SPI interfaces
- **Virtual Threads** — JDK 25 virtual threads for all concurrency; `StructuredTaskScope` for parallel fan-out
- **ReentrantLock over synchronized** — Prevents carrier-thread pinning with virtual threads
- **PostgreSQL Advisory Locks** — Distributed locking without Redis dependency
- **EventPublisher Abstraction** — In-memory default, Kafka adapter optional
- **Spring Modulith** — Compile-time module boundary enforcement

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 (preview features enabled) |
| Framework | Spring Boot 3.5.x, Spring Modulith |
| Build | Gradle 9.x with Spotless formatting |
| Database | PostgreSQL 16+ (primary), MySQL 8.4, MongoDB 7.0 |
| CDC Engine | Debezium |
| Serialization | Jackson (JavaTimeModule) |
| Migrations | Flyway (18 versions) |
| Observability | Micrometer + OpenTelemetry + Prometheus + Grafana |
| Testing | JUnit 5, Testcontainers, ArchUnit, JMH |
| Deployment | Docker, Kubernetes (Kustomize), Helm, Terraform, ArgoCD |
