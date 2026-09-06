# Architecture Decision Records

All ADRs are in `docs/adr/`.

## ADR Index

| ADR | Decision | Status |
|-----|----------|--------|
| [[ADR-001]] | Spring Modulith for module system | Accepted |
| [[ADR-002]] | Hexagonal (Ports & Adapters) Architecture | Accepted |
| [[ADR-003]] | EventPublisher abstraction over direct Kafka | Accepted |
| [[ADR-004]] | Virtual Threads (JDK 25) for all concurrency | Accepted |
| [[ADR-005]] | PostgreSQL for metadata store | Accepted |
| [[ADR-006]] | pgvector for semantic search (deferred) | Deferred |
| [[ADR-007]] | OpenTelemetry for observability | Accepted |
| [[ADR-008]] | REST/HTTP for agent communication | Accepted |
| [[ADR-009]] | Control Plane / Data Plane separation | Accepted |
| [[ADR-010]] | Plugin SDK (syncflow-plugin-api) | Accepted |
| MODULE_SPLIT_DEFERRED | Runtime module split deferred | Deferred |

---

## ADR-001: Spring Modulith

**Context:** Need compile-time module boundaries without full microservice overhead.

**Decision:** Use Spring Modulith for module system.

**Consequences:**
- Compile-time boundary enforcement
- Event-driven inter-module communication
- Single-JVM integration tests
- Future extraction path to microservices

---

## ADR-002: Hexagonal Architecture

**Context:** Core domain logic should not depend on frameworks or infrastructure.

**Decision:** Hexagonal (Ports & Adapters) architecture.

**Consequences:**
- Core has zero framework dependencies
- 33+ core tests run in milliseconds
- Connector isolation via SPI
- Plugin readiness

---

## ADR-003: EventPublisher Abstraction

**Context:** Need to publish events without coupling to Kafka.

**Decision:** `EventPublisher` abstraction with in-memory default.

**Consequences:**
- In-memory default for dev/testing
- Contract stability
- Kafka adapter is ~50 lines when needed

---

## ADR-004: Virtual Threads

**Context:** Need high concurrency without thread pool sizing.

**Decision:** JDK 25 virtual threads for all concurrency.

**Consequences:**
- No thread pool sizing needed
- Blocking I/O is fine
- `StructuredTaskScope` for parallel fan-out
- `ReentrantLock` instead of `synchronized` (prevents carrier-thread pinning)
- `spring.threads.virtual.enabled: true` for Tomcat

---

## ADR-005: PostgreSQL for Metadata

**Context:** Need ACID compliance, JSONB support, and ecosystem compatibility.

**Decision:** PostgreSQL as primary metadata store.

**Consequences:**
- ACID compliance
- JSONB for flexible schemas
- Flyway migrations
- Compatible with Debezium, pgvector

---

## ADR-006: pgvector (Deferred)

**Context:** AI Copilot needs semantic search for knowledge base.

**Decision:** Use pgvector for vector similarity search (deferred until doc count > 1000).

**Consequences:**
- Same database as metadata
- Semantic search for AI Copilot
- Incremental adoption

---

## ADR-007: OpenTelemetry

**Context:** Need end-to-end tracing across services.

**Decision:** OpenTelemetry for observability.

**Consequences:**
- End-to-end tracing
- Vendor neutrality
- MDC integration
- Micrometer bridge

---

## ADR-008: REST/HTTP for Agents

**Context:** Agents need to communicate with control plane.

**Decision:** REST/HTTP (not gRPC).

**Consequences:**
- Existing infrastructure
- Simple request-response contract
- Debuggability with curl
- gRPC deferred for streaming

---

## ADR-009: Control Plane / Data Plane

**Context:** Need security isolation and scalability.

**Decision:** Separate control plane (API) and data plane (agent).

**Consequences:**
- Security: credentials stay in VPC
- Resilience: agents run without control plane
- Scalability: agents scale independently
- Multi-tenancy: agents scoped to tenants

---

## ADR-010: Plugin SDK

**Context:** Need extensibility without modifying core.

**Decision:** Standalone `syncflow-plugin-api` module.

**Consequences:**
- Zero core dependencies
- Isolated ClassLoader per plugin
- Manifest-driven loading
- Versioned compatibility

---

## MODULE_SPLIT_DEFERRED

**Context:** Architecture analysis recommended extracting `syncflow-runtime` module.

**Decision:** Defer module split.

**Rationale:**
- Single consumer today (API module)
- 490+ tests at risk
- JPA types already in natural boundary packages (`com.syncflow.api.{cdc,sync}.entity`)
