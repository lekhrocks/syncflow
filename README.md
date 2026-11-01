# SyncFlow — Enterprise CDC Platform

SyncFlow is a production-grade, connector-based Change Data Capture (CDC) platform for synchronizing data between heterogeneous databases in near real-time.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     syncflow-api                    │
│  REST (Pipeline CRUD)   │  GraphQL  │  OpenAPI      │
├─────────────────────────────────────────────────────┤
│  syncflow-security      │  syncflow-persistence     │
│                         │  (JPA, Flyway migrations) │
├─────────────────────────────────────────────────────┤
│  syncflow-orchestrator  │  syncflow-core            │
│                         │  (SPI, Model, Registry)   │
├─────────────────────────────────────────────────────┤
│               syncflow-connectors                   │
│  PostgreSQL  │  MySQL   │  MongoDB  │  Kafka        │
├─────────────────────────────────────────────────────┤
│  syncflow-common  (Exceptions, Correlation IDs)     │
└─────────────────────────────────────────────────────┘
```

### Modules

| Module | Responsibility |
|--------|---------------|
| `syncflow-common` | Shared utilities: exceptions, correlation IDs, base config |
| `syncflow-core` | Domain model, Connector SPI, registry, pipeline repository/service |
| `syncflow-api` | REST controllers, GraphQL resolver, global error handling, Flyway migrations |
| `syncflow-connectors` | Connector SPI implementations (one class per database type) |
| `syncflow-expression` | SpEL implementation of the `ExpressionEvaluator` port (used by `EXPRESSION` transform rules) |
| `syncflow-persistence` | JPA entities, Spring Data repositories, Flyway migrations |
| `syncflow-runtime` | `DestinationRouter` — routes records to the target writer |
| `syncflow-plugin-api` | Standalone SPI for third-party connector plugins |
| `syncflow-agent` | Standalone agent for distributed execution in customer VPCs |
| `syncflow-security` | Spring Security configuration |

### Key Decisions

- **Hexagonal + Clean Architecture**: Core domain (`syncflow-core`) sits at the center and is **framework-free** (no Spring, no ORM); adapters (`persistence`, `connectors`, `runtime`, `expression`) depend inward on it, and `syncflow-api` is the composition root. Boundaries are enforced by ArchUnit (`syncflow-api/src/test/.../ArchitectureTest.java`). *Two `@Disabled` burn-down rules remain: `api` still reaches into persistence entities (19 classes) and repositories (20 classes).*
- **Ports over framework types**: Cross-cutting capabilities are abstracted behind ports owned by core. E.g. column expressions use the `ExpressionEvaluator` port (SpEL implementation lives in `syncflow-expression`), so core never depends on an expression engine.
- **Connector SPI**: `Connector` interface with `connect()`, `disconnect()`, `validate()`, `discoverSchemas()`, `discoverTables()`, `health()`, `metadata()`. Add a new database by implementing one class.
- **Spring auto-discovery**: Connectors are `@Component` classes automatically discovered by `SpringConnectorRegistry` on startup.
- **In-memory repository first**: `InMemoryPipelineRepository` for iteration; swap to JPA-backed repository when persistence needs stabilize.
- **CQRS-ready**: Write operations go through `PipelineService`; reads through repository. Event log in `PipelineEvent`.

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
