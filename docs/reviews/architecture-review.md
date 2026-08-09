# SyncFlow — Principal Architecture Review

**Scope:** Full end-to-end review of the SyncFlow CDC platform (all 8 Gradle modules + infra).
**Date:** 2026-08-09
**Method:** Reverse-engineered the actual runtime data flow from source (not the ADRs), then audited against the declared architecture.
**Verdict up front:** The platform has a sound **connector-plugin shell** and a handful of genuinely well-built pieces (keyset snapshot pagination, Debezium offset hygiene, AES-GCM credential encryption, identifier taint-guards). But as a whole it is **in-memory prototype software wearing production-grade clothing**: most "enterprise" subsystems are unpersisted `ConcurrentHashMap` stores, several headline runtime paths are broken or incomplete, there are two parallel/duplicate modeling hierarchies, and the CI/k8s/HPA story is aspirational. This reads like an impressive portfolio/demo codebase, not something safe to run against real customer databases.

---

## 1. Reverse-Engineered Architecture (what actually exists)

### 1.1 Module graph (declared vs real)

```
syncflow-plugin-api  (210 LOC, published SDK — mostly unused in-repo)
        │ depends on nothing
syncflow-common  (304 LOC — exceptions, correlation, tenant ids)
        │
syncflow-core  (2439 LOC — Connector SPI, domain records, in-memory repos)
        │  ← "hexagonal core" — only 2 Spring beans, most domain services are dead code
syncflow-connectors  (2830 LOC — Debezium CDC, JDBC metadata/snapshot/writer, validators)
        │
syncflow-security  (1 static class — used by API but the module is a shell)
syncflow-monitoring  (EMPTY module — build.gradle only, zero sources)
syncflow-agent  (172 LOC — agent heartbeat/register client; agent runtime state is IN MEMORY)
        │
syncflow-api  (8458 LOC — ALL controllers, JPA entities, orchestrators, AI, plugin mgr)
```

**Key structural facts:**

- **Two parallel "connection" worlds.** `syncflow-core/.../connection/` holds the rich domain model (`Connection`, `ConnectionProperties`, `ConnectionType` enum, `ConnectionStatus`) and SPI (`ConnectorFactory`, `ConnectionValidator`). `syncflow-core/.../model/ConnectionConfiguration` is a *second*, simpler connection record used by the runtime connectors, writers, `CaptureLifecycle`, `DestinationRouter`, `SnapshotExecutor`, and `MetadataDiscoveryService`. Every hop converts one to the other via a hand-written `toConfig()`/`ConnectorTypeMapper` — **the same `toConfig(Connection)` conversion is copy-pasted into at least 5 classes** (`CaptureLifecycle`, `DestinationRouter`, `SnapshotExecutor` ×3, `MetadataDiscoveryService`).
- **Two parallel "pipeline" worlds.** `syncflow-api/.../pipeline/entity/PipelineEntity` + `PipelineJpaRepository` + `PipelineRepositoryAdapter` + `PipelineEntityMapper` exist and are wired, but `PipelineDesignerService` (the live path) uses the **different** `PipelineDesignEntity`/`PipelineDesignJpaRepository`. The `PipelineEntity` family is redundant and likely orphaned.
- **`PipelineService` + `InMemoryPipelineRepository` + `PipelineEvent` (core)** — a *third* pipeline CRUD path, in-memory, with an unbounded `eventLog`. It is not the live path either (the live path is `PipelineDesignerService`). ADR-005 claims Postgres persistence; the runtime state machines are in-memory.

### 1.2 The actual CDC → sync data flow (source of truth)

```
User → REST controller (SyncController.start / CaptureController.start)
  → SyncOrchestrator.start(pipelineId)
      ├─ CaptureLifecycle.start(pipelineId)
      │     ├─ resolve CdcCapableConnector from SpringConnectorRegistry
      │     ├─ buildPublisher(): KafkaEventPublisher  OR  BoundedQueueEventPublisher
      │     └─ connector.startCDC(ctx, consumer)   → DebeziumEngine on a virtual thread
      │           └─ per-event: parse JSON → CDCEvent → publisher.publish(event)
      │
      └─ start per-pipeline virtual-thread worker:
            LinkedBlockingQueue<CDCEvent>(10k) ← drainTo(100) batch
              → FilterProcessor → TransformProcessor
              → DestinationRouter.write(connectionId, event, destCols)
                    → WriterRegistry → JdbcBatchWriter (JDBC, autoCommit=false)
                    → INSERT INTO currentTable ...
              → EventIdempotencyStore (JPA)  /  RetryEngine  /  DeadLetterQueue (JPA)

SSE: StatusBroadcaster.fan-out ← SnapshotExecutor / SyncOrchestrator.emit()
Snapshot: SnapshotExecutor → SnapshotCapableConnector.readBatch (keyset pagination)
          → FilterProcessor→TransformProcessor → DestinationWriter → checkpoint every 5 batches
```

**Criticality note on ordering:** `SyncOrchestrator.start()` starts **CDC before** the sync worker queue exists, and CDC is a *firehose* — the bounded publisher drops the oldest event when full (by design, `BoundedQueueEventPublisher`). If the sink (snapshot→writer) stalls, the queue overflows and **events are silently dropped with only a WARN**. There is no backpressure to Debezium. For a CDC platform this is a data-loss bug waiting to happen under any load spike.

### 1.3 What the ADRs claim vs what is real

| ADR | Claim | Reality |
|---|---|---|
| ADR-001 Spring Modulith | Compile-time module boundaries | **Not enforced.** No `spring-modulith-starter-core` in any build.gradle, no `@ApplicationModule`, no Modulith tests. The only ArchUnit test (`ArchitectureTest`) exists but Modulith as such is absent. |
| ADR-002 Hexagonal | Core has zero framework deps, SPI only | Core **does** depend on `spring-context`, `hibernate-validator`, Jackson, and (transitively) Lombok. SPI is real, but the "pure domain" claim is overstated; `model/Pipeline` carries JPA-validation annotations. |
| ADR-003 Event-stream abstraction | `EventPublisher` with pluggable transports | Real (`EventPublisher`, `InMemory`, `BoundedQueue`, `KafkaEventPublisher`). Good. But the default production path is the bounded queue with drop-oldest — not Kafka. |
| ADR-004 Virtual threads | All concurrency on virtual threads | Real and well-done (Debezium engine, sync worker, snapshot worker, Kafka consumer). Solid. |
| ADR-005 Postgres metadata | Postgres for pipelines, connections, audit, checkpoints | **Half true.** Connections, pipeline *designs*, DLQ, idempotency, offsets are JPA/Flyway persisted. But pipeline *state machines*, snapshot jobs/progress, sync jobs/statistics, workflow instances, quota, RBAC, audit logs, API keys, agent fleet, alerts, and governance lineage are all **in-memory `ConcurrentHashMap`**. |
| ADR-006 pgvector | Deferred, architecture-ready | The "ready" `KnowledgeBase` is a naive keyword store; nothing vector. Fine as deferred, but the claim of readiness is generous. |
| ADR-007 OpenTelemetry | OTLP traces | Config present (`management.otlp.tracing.endpoint`), but no OTel dependencies are on the classpath — the javaagent starter lib is declared but I saw no agent attach. Trace IDs in the log pattern won't be populated without the agent. |
| ADR-008 Agent REST | 6 REST endpoints, mTLS | Endpoints exist. **mTLS does not exist anywhere** in code or k8s; `/api/agents/*` is not in the public-path allowlist, so agents would need a JWT that the agent module never obtains. The agent runtime state is also in-memory (lost on restart). |
| ADR-009 Control/data plane | Agents self-contained, resilient to CP loss | Agent module exists but is a thin HTTP client; there is no local work queue or checkpoint persistence in the agent — the "resilience" claim isn't implemented. |
| ADR-010 Plugin SDK | Plugins loaded via isolated URLClassLoader | `PluginManager` is real (install/enable/disable from manifest). But there is **no persistence** for installed plugins (lost on restart), no signature/security check on the JAR, and the SDK (`syncflow-plugin-api`) is never consumed by the built-in connectors. |

---

## 2. Bad Architecture Decisions

### 2.1 In-memory "enterprise" subsystems — the core disease
The biggest architectural failure: **state that must survive restarts and be multi-instance safe is in process-local `ConcurrentHashMap`s.** HPA scales replicas 2→10, but each replica has its own private copy of snapshot jobs, sync jobs, workflow state, quota, audit, API keys, and agent fleet — so scale-out actively breaks correctness (a job started on pod A is invisible to pod B; SSE subscribers on A never see events from B). KEDA scaling on `syncflow_queue_depth` / `syncflow_workflow_queue_size` is watching **in-memory gauges per-pod**, which makes autoscaling near-meaningless.

Affected (non-exhaustive): `SnapshotExecutor`, `SyncOrchestrator`, `WorkflowScheduler`, `FleetManager`, `QuotaEngine`, `EnterpriseAuditStore`, `ApiKeyStore`, `DataGovernanceService`, `InMemoryPipelineRepository`, `CheckpointStore`, `StatusBroadcaster` (SSE is per-pod by nature).

### 2.2 `SnapshotExecutor` holds mutable job objects in a shared map that it mutates
`jobs.put(id, job.withProgress(...))` is a publish-on-write pattern with immutable snapshots — actually OK. The real issue is **no persistence of jobs/progress**, plus the job map is never cleaned except on cancel/failure — completed snapshot jobs leak in memory forever (`remove()` is only called on cancel and failure paths, **not on success**).

### 2.3 Two connection models + three pipeline models = "hexagonal" in name
The duplication isn't harmless — the runtime path is the `ConnectionConfiguration` model, the domain model is `Connection`, and the two enums (`ConnectionType` vs `ConnectorType`) must be mapped by hand at every hop. Every new connector touches 6+ files across 3 models. This is the opposite of the ADR-002 goal.

### 2.4 Kafka is bolted on as an optional second path, not the architecture
`CaptureLifecycle.buildPublisher()` branches on `syncflow.kafka.enabled`. When off (default), the CDC→sync handoff is the bounded in-memory queue. When on, Debezium→Kafka producer→Kafka consumer→same in-memory queue→worker. So even "Kafka mode" ends in the same in-memory queue, and Kafka adds a full serialize/deserialize round trip plus topic provisioning. Kafka's actual value (replay, backpressure, multi-consumer) is **not realized** — the consumer is per-pipeline, per-pod, single-threaded.

### 2.5 Agent/Fleet is a control-plane façade with no real distributed protocol
ADR-008/009 describe a fleet of self-contained data-plane agents. Reality: `AgentController` + `FleetManager` (in-memory) + a thin `SyncFlowAgent` client that registers and heartbeats. No mTLS, no auth, no local persistence, no task dispatch that actually executes work.

### 2.6 `syncflow-security` and `syncflow-monitoring` are empty/stub modules
`syncflow-security` has one static helper (`SecurityConfig` with a hardcoded public-path list). `syncflow-monitoring` has **zero sources** — yet the root `build.gradle` wires both into the build and ADR-007 credits them. The real security config lives in `syncflow-api` (`WebSecurityConfig`, `JwtSecurityConfig`). This is module-layout theater.

### 2.7 Workflow subsystem is non-functional
`WorkflowScheduler.completedTaskIds()` **always returns `Set.of()`**. Combined with `tick()` re-enqueuing tasks every 2s, `start()` enqueues all root tasks but nothing ever marks a task complete, so the workflow graph can never progress. `TaskQueue` enqueues with no workers executing task types. The whole `syncflow-core/workflow` + `WorkflowScheduler` + `WorkflowBuilder` is scaffolding with no engine.

### 2.8 `PipelineEvent` / `eventLog` in `PipelineService` is an unbounded in-memory list
`List<PipelineEvent> eventLog` grows forever with every pipeline transition; `events()` returns the whole thing. Minor in practice (core path is dead), but it's a memory leak in the one core service that is a Spring bean.

---

## 3. Duplicate Logic

| Duplicate | Locations | Cost |
|---|---|---|
| `toConfig(Connection)` / `ConnectorTypeMapper.toCore(...)` hand-conversion | `CaptureLifecycle`, `DestinationRouter`, `SnapshotExecutor` (3×), `MetadataDiscoveryService`, `ConnectionController` | Every connector change ripples through 5+ sites |
| `ConnectionType` vs `ConnectorType` enums + manual maps | core/connection vs core/model; `ConnectionValidatorRegistry` (deduces type by probing `supports()`) | Two type systems for one concept |
| Filter/Transform pipeline | `FilterProcessor`/`TransformProcessor` are correct and **reused** by both snapshot and sync — good. But transform `EXPRESSION` type is a no-op stub (`EXPRESSION -> value`) while `PipelineValidator` validates a different rule shape | EXPRESSION silently passes values through |
| PK extraction for events | `PostgresCdcConnector.extractPk` (Debezium key envelope + common-name fallback) vs `MySqlCdcConnector`/`MongoDbCdcConnector` (own copies) | Three near-identical parsers |
| Metadata cache pattern | `MetadataCache` has 5 near-identical `get*/put*` pairs + 5 Caffeine caches | ~100 lines of boilerplate that a single `Cache<String,Object>` or a map of caches would replace |
| Registry pattern | `SpringConnectorRegistry`, `ConnectionValidatorRegistry`, `WriterRegistryImpl`, (plugin) `PluginManager` — 4 hand-rolled registries | Each has its own registration/type-resolution quirks (see WriterRegistry below) |
| Offset save/restore | `OffsetStore` (JPA) vs Debezium `FileOffsetBackingStore` (tmpdir) | Two offset stores that don't talk to each other |

---

## 4. Performance Bottlenecks

1. **Per-event JDBC connection churn (critical).** `DestinationRouter.write()` does `writer.connect()` → `DriverManager.getConnection` → write → `commit()` → `close()` **for every single CDC event**, one event at a time. No connection pooling, no batching across events (only the snapshot path batches via `JdbcBatchWriter`'s internal buffer). At any real CDC rate this is catastrophic — connection setup dominates. `JdbcBatchWriter`'s `writeBatch` is only called with 1 row from the router path.
2. **`JdbcBatchWriter` flush threshold is never reached on the sync path.** It buffers until 1000 rows, but the router commits after each event, so the buffer never fills and `executeBatch` is effectively single-row.
3. **`EventIdempotencyStore.isProcessed()` is a DB `exists()` per event**, plus `markProcessed()` does another `exists()` then `save()`. Two round trips per event, each opening/committing its own transaction (`@Transactional` on the class). No batching, no caching. This is on the hot path.
4. **`RetryEngine` counts retries per eventId in a map that's never cleaned on the non-retry path** (`evaluate` removes only when DLQ-ing); `activeRetries()` grows with distinct failing events that retry-but-never-succeed. Bounded only by DLQ eventual eviction. (Minor memory issue, but on a retry-storm it grows.)
5. **Snapshot `estimateRows` per table + `readBatch` per batch opens one JDBC connection per call** — `AbstractJdbcSnapshotConnector` holds a single `jdbcConnection` but `AbstractJdbcMetadataConnector.connect()` closes/reopens each `ensureConnected`. Actually reconnect only happens when disconnected, so this is fine; the real cost is the snapshot re-reading `fetchPrimaryKey` per batch and the executor calling `connectionService.getWithDecryptedCredentials` (a **decrypt** + DB read) per table per job — cheap but repeated.
6. **SSE fan-out serializes each payload with a fresh `ObjectMapper` write per emit** — fine at low rates; the bigger issue is the unbounded subscriber map with `DEFAULT_TIMEOUT_MS = 0` (no timeout) — dead SSE connections only cleaned on emit error.
7. **`DataGovernanceService.classifyColumns` / schema-history scans** do linear scans of the whole `ConcurrentHashMap` per query (`.values().stream().filter(...).sorted(...)`) — fine at demo scale, O(n) per lookup at scale.
8. **Kafka producer per pipeline with `linger.ms=5` / `batch.size=65536`** is reasonable, but the **consumer is single-threaded per pipeline and commits sync after each poll batch** — throughput ceiling and no parallelism across partitions.

---

## 5. Scalability Risks

1. **Everything is in-memory and per-pod** (2.1) — scale-out breaks correctness, not just availability. The single most important risk.
2. **Per-event JDBC connect (4.1)** — the platform cannot survive real CDC volume on the sync path.
3. **Debezium offset storage in `java.io.tmpdir`** — pod-local, ephemeral, deleted on restart/reschedule. `offset.storage.file.filename` points at `/tmp/syncflow_offset_*.dat`. Combined with the JPA `OffsetStore` never being fed back into Debezium, **offset persistence is broken across restarts** — a restarted pipeline re-reads from `FileOffsetBackingStore` in a wiped tmpdir → potential re-processing or missed events. This is the #2 critical risk after the in-memory store.
4. **Postgres slot leak / name collision.** `PostgresCdcConnector.specificProperties()` builds `slot.name`/`publication.name` from **`sanitize(config.database())` only** — the comment says "pipeline-specific suffix via pipelineId" but the code **never uses the pipelineId**. Two pipelines on the same database → **same replication slot and publication → hard conflict**, and `slot.drop.on.stop=false` means slots leak on the source DB forever. Production footgun.
5. **`BoundedQueueEventPublisher` drop-oldest (1.2)** — no backpressure; data loss under load. Also the sync worker drains with `drainTo(100)`/500ms poll while CDC can produce far faster.
6. **`SnapshotExecutor` checkpoint is in-memory** — a restart mid-snapshot loses the resume cursor (re-keysets from scratch, or worse with OFFSET fallback). The checkpoint feature the code carefully builds (every-5-batches) is non-durable.
7. **HPA/KEDA scale out of a single Postgres** — connections/dlpq/idempotency/offsets all hit one DB; pool capped at 10 (ADR says 10), which the per-event connect churn will exhaust instantly.
8. **No rate limiting or quota enforcement on hot paths** — `QuotaEngine.checkLimit` exists but nothing calls it on pipeline create/sync.

---

## 6. Correctness Bugs (confirmed in source)

1. **`JdbcBatchWriter.currentTable` is never assigned.** `buildInsertSql()` interpolates `"INSERT INTO " + currentTable` where `currentTable` is a field declared but never written (it's `null` → `"INSERT INTO null (...)"`, an immediate SQLException). The `DestinationRouter` passes `tableName` into `writeBatch(table, ...)` but `writeBatch` **ignores its `table` parameter** and uses `buffer.getFirst().keySet()` for columns. **The CDC sync write path cannot write a single row.** This is the #1 bug — the headline "sync" feature is broken end to end.
2. **`BoundedQueueEventPublisher` drops the oldest event on overflow** — silent data loss (documented in class, but it's a policy that defeats the purpose of a CDC platform).
3. **`WorkflowScheduler.completedTaskIds()` returns empty** — workflow engine can never complete (2.7).
4. **`DeadLetterQueue.replay()` only sets `replayedAt`/marks it** — it does **not re-submit the event to `SyncOrchestrator`**. Replay is a no-op flag flip. (I recall this was covered in earlier memory — confirmed again here.)
5. **`MySqlWriter`/`PostgresWriter` jdbcUrl/props duplication** is fine, but `JdbcBatchWriter.flush()` uses `buffer.getFirst().keySet()` for columns — row-key order isn't guaranteed stable across `LinkedHashMap` rows; mixed key sets across rows → wrong SQL. (Minor vs #1.)
6. **`CaptureLifecycle.stop()`/`shutdownAll()` flush+close the publisher but the publisher may be the in-memory `BoundedQueueEventPublisher` whose buffered events are simply discarded.** Pending events between Debezium offset and consumer are lost on stop.
7. **`RetryEngine` on transient failure increments `retries` but never actually retries** — `processEvent` moves on; the "retry" is only counted, and `RetryDecision.delay` is never used to re-deliver. So transient errors are counted as retries then DLQ'd at MAX_RETRIES without ever re-attempting. The retry mechanism is a counter, not a retry.
8. **Hardcoded encryption key + JWT secret in `application.yml`**: `syncflow.encryption.key: MDEyMzQ1Njc4OWFiY2RlZg==` (the literal bytes `0123456789abcdef`) and a `jwt.secret` default. K8s deployment overrides the encryption key via secret, but **no JWT secret is set in k8s** — falls back to the committed default. Anyone with repo access can forge tokens.
9. **`JwtProperties` default secret contains a `?`** (`...LWRUV9jaGFuZ2UtaW4tcHJvZA==`), which is **not valid base64** → `JwtSecurityConfig.secretKey()` throws on startup if `SYNCFLOW_JWT_SECRET` is unset. The app likely **fails to boot** in a plain `docker compose` environment. (Contradicts the "works locally" story.)
10. **Tenant context is header-injectable**: `TenantFilter` accepts `X-Tenant-Id` as a **plain HTTP header** and uses it verbatim for scoping (unless overridden by JWT subject). With `fail-on-unknown-properties:false` and no claim mapping, a caller can set `X-Tenant-Id: <other>` and, on any endpoint that only checks `TenantContextHolder`, operate in another tenant's scope. RBAC checks (`AuthorizationService`) are only invoked in `AdminController`; most controllers (`ConnectionController`, `PipelineDesignerController`, `SyncController`, `SnapshotController`) do **no authorization** beyond authentication.
11. **`AdminController.createOrg/createWorkspace/createProject` are stubs** — they generate an id and return it without persisting anything.
12. **Agent endpoints (`/api/agents/*`) are authenticated** but the agent has no way to authenticate (no JWT issuance flow for agents) — the control-plane/agent loop is unreachable in practice.

---

## 7. Maintainability Issues

- **135+ source files with 20+ hand-rolled in-memory maps and repeated conversion helpers** — the "clean hexagonal" ADR narrative doesn't match the code, making it hard for a new engineer to know which of the 3 pipeline/2 connection models is real.
- **MapStruct is used (`ConnectionMapper`, `PipelineDesignEntityMapper`) but half the mappings are hand-written** (Jackson round-trips, `toConfig` helpers) — two mapping paradigms side by side.
- **`New ObjectMapper()` created ad hoc** in `ConnectionMapper.toJson/parseOptions` and `ConnectionService.serializeOptions` — ignoring the Spring-injected singleton (which has JSR-310 modules configured). Non-deterministic date handling, wasted allocations.
- **`CaptureLifecycle` / `SyncOrchestrator` / `SnapshotExecutor` are 200-300 line god-components** doing lifecycle + routing + metrics + SSE + threading in one class.
- **Magic strings everywhere**: `"syncflow.sync.events.processed"`, `"pipeline"` tags, status names (`"STOPPED"`, `"RUNNING"`) returned as raw `Map.of(...)` in controllers — no response DTOs for most endpoints.
- **`SnapshotExecutor.remove()` never called on success** → completed job objects leak (also 2.2).
- **`FleetManager`/`QuotaEngine`/`EnterpriseAuditStore`/`ApiKeyStore`/`AlertEngine` all `@Component` with in-memory state** — they masquerade as persistent enterprise services.
- **`README`/ADR/CHANGELOG describe capabilities the code doesn't have** (Modulith, OTel, mTLS, plugin persistence, workflow execution) — documentation drift makes onboarding actively misleading.

---

## 8. A Clean Architecture Breakdown (target)

### 8.1 Target module layout

```
syncflow-plugin-api        (unchanged — the real SPI, no framework deps)
syncflow-core              (pure domain + SPI — remove spring-context, hibernate-validator, Jackson, Lombok from API surface)
syncflow-connectors        (adapters — keep; split per-db modules later if they grow)
syncflow-api               (control-plane: REST/GraphQL adapters, JPA, security)  ← slims down
syncflow-agent             (data-plane runtime — must gain local persistence + task executor)
syncflow-monitoring        (DELETE or actually implement)
syncflow-security          (DELETE; fold the one static helper into syncflow-api)
syncflow-common            (keep — exceptions, tenant ids, correlation)
```

### 8.2 The one modeling correction that fixes the most duplication

**Collapse the two connection models into one.** Make `ConnectionConfiguration` (or the `Connection` domain record) the single type flowing through the SPI, delete `toConfig`/`ConnectorTypeMapper`, and keep **one** enum. This removes ~6 conversion sites and the type-mapping matrix. Same for pipeline: pick `PipelineDesignEntity` as the persistence model, delete `PipelineEntity`/`PipelineRepositoryAdapter`/`InMemoryPipelineRepository`/core `PipelineService` (or make the core service the single source of truth and delete the API's).

### 8.3 State that must move to Postgres (or Redis) to be production-safe

| Subsystem | Now | Should be |
|---|---|---|
| Snapshot jobs / progress | in-memory map | Postgres table + `@Entity` (progress rows) |
| Sync jobs / statistics | in-memory map | Postgres table |
| Workflow instances/executions | in-memory + non-functional | Postgres + a real task worker |
| Quota, RBAC policy, API keys, audit | in-memory | Postgres |
| Agent fleet / heartbeats | in-memory | Postgres (or Redis) + staleness sweeper |
| DLQ replay | flag-only | real re-enqueue to the sync queue |
| Checkpoints | in-memory | Postgres (resume after restart) |

### 8.4 The two runtime paths to unify

**Delete the bounded-queue handoff; make Kafka (or a durable queue) the only transport.** Then:
- Debezium → Kafka (persistent, replayable, backpressurable) → partition-parallel consumers → idempotent writer.
- Remove `BoundedQueueEventPublisher` from production; keep it for tests only.
- Backpressure becomes Kafka's `max.poll.records` + consumer lag, not drop-oldest.

### 8.5 Fix the connector model so adapters stop reimplementing everything

Give `AbstractJdbcMetadataConnector`/`AbstractJdbcSnapshotConnector`/`AbstractConnectorValidator`/`JdbcBatchWriter` **real shared implementations** (connection pooling via `HikariDataSource`, single shared SQL build), and have each DB subclass supply only URL/type. Delete the stub `PostgresConnector`. Make `WriterRegistryImpl` resolve by `supports()` like `ConnectionValidatorRegistry` instead of `instanceof` checks.

---

## 9. Refactoring Strategies (prioritized)

**Tier 1 — makes the product not lie about itself (do first, highest ROI):**

1. Fix `JdbcBatchWriter.currentTable` + honor the `table`/`columns` parameters; add a writer integration test against Testcontainers Postgres. *(One-line fix, unblocks the entire sync path.)*
2. Decide the single connection + single pipeline model; delete the duplicates and the `toConfig` matrix. *(Biggest structural cleanup, kills the most duplication.)*
3. Move runtime job state (snapshot/sync/workflow) to JPA entities. Use an outbox/event table to persist status transitions so SSE + cross-pod reads work.
4. Make Debezium offsets durable: use a Postgres-backed `JdbcOffsetBackingStore` (or feed the JPA `OffsetStore` into Debezium) instead of `/tmp` files. Include `pipelineId` in slot/publication names and add `slot.drop.on.stop` per pipeline lifecycle.

**Tier 2 — scale/performance:**

5. Connection pooling for writers (inject a shared `DataSource`), and batch events into the writer instead of connect/commit/close per event.
6. Batch idempotency checks (`IN` query) and cache processed IDs in-process with TTL.
7. Real retry delivery in `RetryEngine` (scheduled re-enqueue with backoff), or hand responsibility to Kafka.
8. Make checkpointing durable and call `remove()` on completed snapshots.

**Tier 3 — hardening/security:**

9. Move `syncflow.encryption.key` and `jwt.secret` out of committed defaults; **fail fast with a clear error** instead of shipping a weak/`?`-corrupt default. Fix the `?` in the JWT default.
10. Tenant scoping from the **authenticated principal only** (drop trust in `X-Tenant-Id` headers), and add `AuthorizationService` checks to `ConnectionController`/`PipelineDesignerController`/`SyncController`/`SnapshotController`.
11. Remove or genuinely implement the workflow engine; if kept, wire a real task executor and persist execution state.
12. Implement or delete `syncflow-monitoring`/`syncflow-security` as separate modules; implement mTLS + agent auth or remove the claim.

---

## 10. Production-Grade Code Samples

### 10.1 Fix the broken writer (Tier 1, #1)

```java
// syncflow-connectors/.../writer/JdbcBatchWriter.java — corrected core
public abstract class JdbcBatchWriter implements DestinationWriter {
    protected Connection connection;          // inject a DataSource instead for pooling
    private String currentTable;
    private List<String> currentColumns;
    private final List<Map<String, Object>> buffer = new ArrayList<>();

    @Override
    public void writeBatch(String table, List<Map<String, Object>> rows, List<String> columns) {
        if (rows.isEmpty()) return;
        if (currentTable == null) { currentTable = table; currentColumns = columns; }
        else if (!currentTable.equals(table)) { flush(); currentTable = table; currentColumns = columns; }
        buffer.addAll(rows);
        if (buffer.size() >= 1000) flush();
    }

    @Override
    public void flush() {
        if (buffer.isEmpty() || connection == null) return;
        var cols = currentColumns;                       // use the passed columns, not row keyset
        var sql = buildInsertSql(cols);
        try (var stmt = connection.prepareStatement(sql)) {
            for (var row : buffer) {
                for (int i = 0; i < cols.size(); i++) stmt.setObject(i + 1, row.get(cols.get(i)));
                stmt.addBatch();
            }
            stmt.executeBatch();
            buffer.clear();
        } catch (SQLException e) { throw new RuntimeException("Batch write failed", e); }
    }
}
```
**Note:** this still opens a JDBC connection per `connect()`; the durable fix is to inject a pooled `DataSource` and remove the per-event `connect`/`commit`/`close` in `DestinationRouter`.

### 10.2 Durable offset for Debezium (Tier 1, #4)

```java
// Postgres-backed offset store passed to Debezium instead of FileOffsetBackingStore
debeziumProps.setProperty("offset.storage",
        "org.apache.kafka.connect.storage.JdbcOffsetBackingStore");
debeziumProps.setProperty("offset.storage.jdbc.url", jdbcUrl);
debeziumProps.setProperty("offset.storage.jdbc.user", user);
debeziumProps.setProperty("offset.storage.jdbc.password", password);
debeziumProps.setProperty("offset.storage.table.name", "debezium_offsets");
// And scope slot/publication per pipeline so two pipelines on one DB don't collide:
props.setProperty("slot.name", "syncflow_slot_" + sanitize(db) + "_" + sanitize(pipelineId));
props.setProperty("publication.name", "syncflow_pub_" + sanitize(db) + "_" + sanitize(pipelineId));
```
The `pipelineId` must come from `ctx.runtimeProperties()` (already available) and be threaded into `specificProperties(config)` — today the parameter is ignored.

### 10.3 Backpressure without drop-oldest (Tier 1, #3)

```java
// Give Debezium a blocking publisher so overflow backpressures instead of dropping.
public class BackpressuredEventPublisher implements EventPublisher {
    private final BlockingQueue<CDCEvent> queue = new LinkedBlockingQueue<>(CAPACITY);
    @Override public void publish(CDCEvent e) {
        try { queue.put(e); }                 // blocks the Debezium thread when full
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
    // drain() unchanged; consumers apply Kafka-style max.poll semantics.
}
```
This converts silent data loss into backpressure (CDC pauses until the sink catches up). Pair with a WARN when `queue.size()` stays near capacity so ops can scale the sink.

### 10.4 Tenant scoping from principal only (Tier 3, #10)

```java
// TenantFilter: never trust the header unless it matches the authenticated subject.
var auth = SecurityContextHolder.getContext().getAuthentication();
if (auth == null || !auth.isAuthenticated()) {
    // allow only DEFAULT tenant / anonymous for public endpoints
    TenantContextHolder.set(TenantContext.anonymous());
    chain.doFilter(request, response); return;
}
// Derive tenant from the JWT claim (e.g. "tenant") — ignore X-Tenant-Id entirely.
var tenant = (String) ((Jwt) auth.getPrincipal()).getClaims().get("tenant");
TenantContextHolder.set(TenantContext.of(TenantId.from(tenant), auth.getName(), rolesOf(auth)));
```

### 10.5 Batch idempotency (Tier 2, #6)

```java
// EventIdempotencyStore — batch + in-process TTL cache instead of per-event DB round trips
@Component
public class EventIdempotencyStore {
    private final Cache<String, Boolean> recent = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofHours(24)).build();

    public boolean isProcessed(String eventId) {
        var cached = recent.getIfPresent(eventId);
        if (cached != null) return true;
        return repository.existsByEventId(eventId);   // first miss only
    }
    public void markProcessed(String eventId) {
        recent.put(eventId, Boolean.TRUE);
        // debounced async flush to DB (outbox) — not in the hot path
    }
}
```
This cuts per-event DB traffic from 2 queries to 0 on the hot path.

---

## 11. Critical Problem Areas — Ranked

1. **Sync write path is broken** (`JdbcBatchWriter.currentTable` → `INSERT INTO null`; router commits per event, so the sync feature cannot persist a row). **P0.**
2. **Everything critical is in-memory & per-pod** — scale-out breaks correctness; HPA/KEDA are misleading. **P0.**
3. **Debezium offsets in `/tmp` + persisted `OffsetStore` never feeds back** — restarts lose position; **plus slot/publication name collision on same-DB pipelines** with slots that never drop. **P0/P1.**
4. **No backpressure — drop-oldest under load** = silent CDC data loss. **P1.**
5. **Security defaults**: committed AES key, `?`-corrupt JWT default, header-trusted tenant scoping, minimal authorization on most controllers. **P1.**
6. **Kafka path adds latency without adding replay/backpressure value**, and the default is the queue. **P2 (architectural).**
7. **Workflow engine non-functional; DLQ replay is a no-op; retries are counters not retries.** **P1/P2.**
8. **Two connection models + three pipeline models** — the "hexagonal" claim is structural debt. **P2.**

---

## 12. Bottom Line

The codebase is a **strong portfolio-grade CDC platform with a real connector SPI, real Debezium wiring, keyset pagination, and credential encryption** — but the runtime orchestration layer (sync, snapshot, workflow, agent, enterprise ops) is **unpersisted, single-pod, and in several places non-functional**. Before it can run against real customer databases the priority order is: **(1)** fix the writer bug, **(2)** make runtime state durable, **(3)** make offsets and replication slots durable and pipeline-scoped, **(4)** add real backpressure, **(5)** fix the security defaults and tenant scoping. The clean architecture target collapses to: one connection model, one pipeline model, one event transport (durable), and every state machine backed by Postgres.

*This document was produced as a read-only analysis. No code was changed.*
