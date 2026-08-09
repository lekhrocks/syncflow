# SyncFlow — Enterprise CDC Platform

SyncFlow is a production-grade, connector-based Change Data Capture (CDC) platform for synchronizing data between heterogeneous databases in near real-time.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     syncflow-api                    │
│  REST (Pipeline CRUD)   │  GraphQL  │  OpenAPI      │
├─────────────────────────────────────────────────────┤
│  syncflow-security      │  syncflow-monitoring      │
├─────────────────────────────────────────────────────┤
│  syncflow-core            │
│  (SPI, Model, Registry)   │
├─────────────────────────────────────────────────────┤
│               syncflow-connectors                   │
│  PostgreSQL  │  MySQL   │  MongoDB  │  Kafka        │
├─────────────────────────────────────────────────────┤
│  syncflow-common  (Exceptions, Tenant IDs)          │
└─────────────────────────────────────────────────────┘
```

### Modules

| Module | Responsibility |
|--------|---------------|
| `syncflow-common` | Shared utilities: exceptions, tenant IDs, correlation |
| `syncflow-core` | Domain model, Connector SPI, registry |
| `syncflow-api` | REST controllers, JPA persistence, Flyway migrations, orchestrators |
| `syncflow-connectors` | Connector SPI implementations (one class per database type) |
| `syncflow-agent` | Data-plane agent (registration + heartbeat client) |
| `syncflow-plugin-api` | Third-party connector SDK (published artifact) |

### Key Decisions

- **Hexagonal + Clean Architecture**: Core domain (`syncflow-core`) has zero dependencies on web framework or database drivers.
- **Connector SPI**: `Connector` interface with `connect()`, `disconnect()`, `validate()`, `discoverSchemas()`, `discoverTables()`, `health()`, `metadata()`. Add a new database by implementing one class.
- **Spring auto-discovery**: Connectors are `@Component` classes automatically discovered by `SpringConnectorRegistry` on startup.
- **Persistent state**: Connections, pipeline designs, DLQ, idempotency, and CDC offsets persist in PostgreSQL via JPA + Flyway. Runtime job state is persisted so replicas stay consistent.
- **Pipeline designer**: Pipeline definitions (designs + version history) are the live model via `PipelineDesignerService` / `PipelineDesignEntity`.

## Getting Started

### Prerequisites

- Java 25 (with `--enable-preview`)
- Docker & Docker Compose
- Gradle 8.x

### Build

```bash
./gradlew clean build
```

### Run (local)

```bash
# Start PostgreSQL
docker compose -f docker/docker-compose.yml up -d postgres

# Run the app
./gradlew :syncflow-api:bootRun --args='--spring.profiles.active=local'
```

### Run (full stack)

```bash
docker compose -f docker/docker-compose.yml up --build
```

## API

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines` | Create pipeline |
| `GET` | `/api/pipelines` | List all pipelines |
| `GET` | `/api/pipelines/{id}` | Get pipeline by ID |
| `PUT` | `/api/pipelines/{id}` | Update pipeline |
| `DELETE` | `/api/pipelines/{id}` | Delete pipeline |
| `POST` | `/api/pipelines/{id}/start` | Start pipeline |
| `POST` | `/api/pipelines/{id}/stop` | Stop pipeline |
| `POST` | `/api/connections/test` | Test connection |
| `GET` | `/api/health` | Health check |

### GraphQL

- **Endpoint**: `/graphql`
- **IDE**: `/graphiql`

### OpenAPI

- **Swagger UI**: `/swagger-ui.html`
- **API Docs**: `/v3/api-docs`

## Next Iterations

- [ ] JPA-backed pipeline repository (replace in-memory)
- [ ] Debezium-based PostgreSQL CDC connector
- [ ] Kafka event streaming for change events
- [ ] Actual snapshot reading and change event capture
- [ ] WebSocket/SSE for live pipeline status
- [ ] Multi-tenant support
- [ ] Metrics dashboard (Grafana)

## Tech Stack

Java 25 · Spring Boot 3.5.x · Spring Modulith · Gradle · PostgreSQL · Kafka · MongoDB · Docker · Testcontainers · Micrometer · OpenTelemetry · Flyway · GraphQL
