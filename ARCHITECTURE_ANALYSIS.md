# SyncFlow Architecture Analysis

**Generated:** 2026-08-12  
**Last updated:** 2026-09-06 (F16 structured concurrency — see item statuses below)  
**Scope:** End-to-end codebase review (core, api, connectors, common, agent)

---

## 1. Architecture Overview

### 1.1 Module Structure

| Module | Responsibility | Key Components |
|--------|----------------|----------------|
| `syncflow-common` | Shared primitives, tenant context, exceptions | `TenantContextHolder`, `SyncFlowException`, `Agent` domain |
| `syncflow-core` | Domain models, SPI interfaces, pipeline/snapshot/CDC logic | `Pipeline`, `SnapshotJob`, `CDCEvent`, connectors SPI |
| `syncflow-connectors` | Concrete connector implementations (JDBC, Debezium, MongoDB, writers) | `PostgresCdcConnector`, `MySqlCdcConnector`, `JdbcBatchWriter` |
| `syncflow-api` | REST API, orchestration, runtime state, multi-tenancy | `SnapshotExecutor`, `SyncOrchestrator`, `CaptureLifecycle` |
| `syncflow-agent` | Standalone agent for distributed execution | `AgentRegistrar`, `HeartbeatSender` |

### 1.2 Data Flow Summary

```
┌──────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Pipeline   │────▶│ SnapshotExecutor│────▶│ Source Connector │
│   Design     │     │ (batch read +   │     │ (keyset/offset   │
│  (DDL + map) │     │  transform +    │     │  pagination)     │
└──────────────┘     │  write)         │     └────────┬─────────┘
                     └────────┬────────┘              │
                              │ CDC events            ▼
                     ┌────────▼────────┐     ┌──────────────────┐
                     │ CaptureLifecycle│────▶│ Debezium Engine  │
                     │ (start/stop/    │     │ (WAL/binlog     │
                     │  pause/resume)  │     │  streaming)      │
                     └────────┬────────┘     └────────┬─────────┘
                              │ CDC events            │
                     ┌────────▼────────┐              │
                     │ SyncOrchestrator│◀────┐        │
                     │ (queue +        │     │        │
                     │  process +      │     │        │
                     │  route + DLQ)   │     │        │
                     └────────┬────────┘     │        │
                              │             │        │
                     ┌────────▼────────┐    │        │
                     │ DestinationRouter│───┘        │
                     │ (writer per     │              │
                     │  connection)    │              │
                     └─────────────────┘              │
```

---

## 2. Critical Problem Areas

### 2.1 Architecture & Design Flaws

| # | Issue | Location | Severity | Impact | Status |
|---|-------|----------|----------|--------|--------|
| **A1** | **In-memory runtime state maps** (`ConcurrentHashMap`) used for active jobs/captures | `SnapshotExecutor.cancellations`, `CaptureLifecycle.activeCaptures`, `SyncOrchestrator.eventQueues` | **HIGH** | State lost on pod restart; no HA; memory leaks if not cleaned; cannot scale horizontally | (see S1) |
| **A2** | **Virtual threads + ThreadLocal tenant context** — broken by design | `TenantContextHolder` (ThreadLocal) + `Thread.startVirtualThread()` | **HIGH** | Tenant leakage across requests; security boundary violation; `TenantSupport.workerContext()` hack required | (see S4) |
| **A3** | **Single-table assumption in SyncOrchestrator** | `SyncOrchestrator.start()` line 120: `pipeline.tableMappings().stream().findFirst()` | **HIGH** | Only first table mapping processed; multi-table pipelines silently broken | ✅ **Done** — iterates all `tableMappings` with dispatch map |
| **A4** | **DELETE operations not implemented in writer** | `DestinationRouter.java:48` — `// ponytail: DELETE via writer not yet supported` | **MEDIUM** | Data drift; deletes not propagated to destination | ✅ **Done** — `JdbcBatchWriter.deleteBatch()` with composite-IN DELETE |
| **A5** | **No exactly-once semantics for CDC** | `CaptureLifecycle` + `SyncOrchestrator` — idempotency only at event level, not transaction | **MEDIUM** | Duplicate events on restart; no transaction boundary preservation | (see F14) |
| **A6** | **PipelineRepository is in-memory** | `InMemoryPipelineRepository` used in tests; no persistent impl visible in core | **MEDIUM** | Core module lacks persistence abstraction; API module has JPA entities but core doesn't define SPI | (see F12) |
| **A7** | **Tight coupling: API module imports core SPI + concrete domain** | `syncflow-api` depends on `syncflow-core` SPI and domain models | **MEDIUM** | Violates clean architecture; core should not know about API; API should depend on core interfaces only | (see F11) |
| **A8** | **No circuit breaker / backpressure on event queue** | `SyncOrchestrator` uses unbounded `LinkedBlockingQueue(10000)` | **MEDIUM** | OOME risk under burst; no flow control | (see F7) |

---

### 2.2 Duplicate Logic

| # | Duplicated Logic | Locations | Recommendation | Status |
|---|------------------|-----------|----------------|--------|
| **D1** | **ConnectionConfiguration construction** from `Connection` entity | `SnapshotExecutor.toConfig()`, `SyncOrchestrator.toConfig()`, `CaptureLifecycle.toConfig()`, `DestinationRouter.toConfig()`, `PipelineDesignerService.toConfig()` (5 copies) | Extract to `ConnectionMapper` utility in `syncflow-common` or `syncflow-api` | (see F10) |
| **D2** | **Offset store key = pipelineId** (hardcoded) | `CaptureLifecycle.start()`, `CaptureLifecycle.stop()`, `OffsetStore` interface | Make configurable; support multi-table offsets via composite key | ✅ **Done** — key is `tenantId:pipelineId` composite |
| **D3** | **Event publishing to publisher** (counter + publish) | `CaptureLifecycle.start()` line 100-105, `SnapshotExecutor` doesn't publish | Unify event emission via `EventPublisher` abstraction | ✅ **Done** — single `EventPublisher` interface, mutually exclusive impls |
| **D4** | **BatchInformation cursor/offset calculation** | `SnapshotPlannerUnitTest` mirrors logic from `AbstractJdbcSnapshotConnector.readBatch()` | Move to shared `PaginationUtil` | ✅ **By design** — `BatchInformation` is a simple record; test mirrors connector logic intentionally |
| **D5** | **ValidationResult pattern** (ok/failed) | `ValidationResult` in core SPI + `ValidationResult` in pipeline validation (different packages) | Unify into single `ValidationResult` in `syncflow-common` | (see M1) |
| **D6** | **Metrics counter/timer boilerplate** | Every executor/orchestrator repeats `meterRegistry.counter(...)` patterns | Create `MetricsHelper` with `incrementCounter()`, `recordTimer()` | ✅ **Done** — `MetricsHelper` with `increment(registry, name, tags...)` used by all 23 call sites |

---

### 2.3 Performance Bottlenecks

| # | Bottleneck | Location | Why It Matters | Status |
|---|------------|----------|----------------|--------|
| **P1** | **Per-event writer connect/commit/close** | `DestinationRouter.write()` lines 32-58 | New DB connection + transaction per CDC event = catastrophic latency | (see S5) |
| **P2** | **No connection pooling in writers** | `JdbcBatchWriter.connect()` creates raw `DriverManager.getConnection()` | No pooling; connection storm under load | (see S5) |
| **P3** | **Virtual thread per pipeline** (unbounded) | `SnapshotExecutor.start()`, `SyncOrchestrator.start()` | Thread explosion with many pipelines; no pool sizing | ✅ **Done** — bounded worker pool `min(parallelism, workItems.size())` |
| **P4** | **Jackson ObjectMapper per connector instance** | `PostgresCdcConnector.MAPPER`, `MySqlCdcConnector.MAPPER` (static but per-class) | Acceptable but could be shared; minor | Acceptable |
| **P5** | **Synchronous flush/commit per batch in snapshot** | `SnapshotExecutor.executeInner()` lines 246-248 | Blocks virtual thread; should batch commits | ✅ **Done** — single `flush()`+`commit()` after all workers join |
| **P6** | **Keyset pagination uses string cursor comparison** | `AbstractJdbcSnapshotConnector.readKeysetPage()` line 91: `stmt.setObject(1, cursor)` | Lexicographic comparison breaks for numeric/uuid PKs if cursor not same type. **Status (2026-08-17): ✅ fixed** — numeric cursors bound as `Long` (fixes `bigint >= character varying`) |
| **P7** | **No batching in SyncOrchestrator event processing** | `runInner()` drains max 100 events, processes one-by-one | Writer called per event (see P1); should batch writes | ✅ **Done** — `drainTo` + writeBuffer/deleteBuffer → `router.writeBatch()` per table |

---

### 2.4 Scalability Risks

| # | Risk | Details |
|---|------|---------|
| **S1** | **In-memory maps prevent horizontal scaling** | `activeCaptures`, `eventQueues`, `runningFlags`, `cancellations`, `tenantOf` — all `ConcurrentHashMap` in single JVM. ~~State lost on restart~~. **Status (2026-08-17):** durable shape done — jobs/status/statistics/checkpoints/captures/offsets/processed-events all persist (V12–V14 + earlier). Remaining `eventQueues`/`runningFlags`/`cancellations`/`tenantOf` are ephemeral by design (live queues + cancellation flags can't be persisted) |
| **S2** | **No distributed locking for pipeline operations** | Concurrent `start()` on same pipeline from different pods = duplicate CDC engines. **Status (2026-08-17): ✅ done** — `DistributedLockService` (Postgres advisory) guards CDC capture and snapshot start |
| **S3** | **Debezium offset store uses single table** | `debezium_offsets` table with `pipelineId` key — no partitioning; contention at scale. **Status:** unchanged; now tenant-keyed (`tenantId:pipelineId`) |
| **S4** | **Tenant context via ThreadLocal** | Fundamentally incompatible with virtual threads / reactive; breaks in any async boundary. **Status (2026-08-17): ✅ mitigated** — `TenantContext` threaded explicitly through orchestrators/workers; worker threads assert no ThreadLocal |
| **S5** | **Single writer connection per event** | `DestinationRouter` opens/closes connection per `write()` call — cannot scale. **Status (2026-08-17): ✅ done** — `PooledJdbcBatchWriter` + HikariCP, batched writes |
| **S6** | **No partitioning/sharding strategy for large tables** | Snapshot reads entire table sequentially; no parallel chunking. **Status (2026-08-17): ✅ done** — parallel PK-range chunking (F15) |

---

### 2.5 Maintainability Issues

| # | Issue | Impact | Status |
|---|-------|--------|--------|
| **M1** | **Two `ValidationResult` classes** | `com.syncflow.core.spi.ValidationResult` vs `com.syncflow.core.pipeline.validation.ValidationResult` — confusion, not unified | ✅ **Done** — SPI one renamed to `ConnectorValidationResult`; pipeline one kept with distinct shape |
| **M2** | **Two `ProcessingContext` classes** | `com.syncflow.core.snapshot.pipeline.ProcessingContext` vs `com.syncflow.core.sync.ProcessingContext` — same name, different packages | ✅ **Done** — dead `core.sync.ProcessingContext` deleted (zero usages) |
| **M3** | **Core module has no persistence SPI** | `PipelineRepository` is interface but only `InMemoryPipelineRepository` in core; JPA entities only in API | (see F12) |
| **M4** | **`@Transactional` on read-only methods with validation that throws** | `PipelineDesignerService.validate()` explicitly avoids `@Transactional` due to `UnexpectedRollbackException` — symptom of wrong exception handling | ✅ **By design** — intentional omission with explanatory comment |
| **M5** | **Magic numbers / hardcoded values** | `QUEUE_CAPACITY=10000`, `MAX_RETRIES=3`, `BASE_DELAY_MS=1000`, checkpoint every 5 batches — not configurable | (see F8) |
| **M6** | **`ponytail:` comments indicate known debt** | `DestinationRouter:49` — "DELETE via writer not yet supported" | ✅ **Done** — DELETE fully implemented in `JdbcBatchWriter.deleteBatch()` |
| **M7** | **Inconsistent error handling** | Some methods throw `SyncFlowException`, others `IllegalArgumentException`, others `RuntimeException` — no unified strategy | ✅ **Done** — `GlobalExceptionHandler` now maps `IllegalArgumentException`→400, `NoSuchElementException`→404, `IllegalStateException`→409 |
| **M8** | **`SnapshotExecutor` does too much** | 350+ lines: orchestration + persistence + metrics + tenant context + checkpointing + event emission — violates SRP | ✅ **Done** — `snapshotRange` + `cursorWithinRange` extracted to `SnapshotWorker` (591→443 lines) |

---

## 3. Clean Architecture Breakdown

### 3.1 Current Layering (Problematic)

```
┌─────────────────────────────────────────────────────────────┐
│                      syncflow-api                           │
│  Controllers, Orchestrators, JPA Entities, Repositories    │
│  ▼ depends on ▼                                             │
│                      syncflow-core                          │
│  Domain Models, SPI Interfaces, In-Memory Repos            │
│  ▼ depends on ▼                                             │
│                    syncflow-common                          │
│  TenantContext, Exceptions, Base Types                      │
└─────────────────────────────────────────────────────────────┘
          ▲                         ▲
          │ implements              │ uses
┌─────────┴─────────┐     ┌─────────┴─────────┐
│ syncflow-connectors    │   (external)      │
│ Debezium, JDBC,       │                     │
│ Writers, MongoDB      │                     │
└───────────────────────┘                     │
```

**Violations:**
- API module contains business logic (`SyncOrchestrator`, `SnapshotExecutor`) — should be in core or a separate `syncflow-runtime`
- Core defines SPI but API implements it — inverted dependency
- Connectors depend on core SPI (correct) but core has no persistence SPI

---

### 3.2 Recommended Clean Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        syncflow-api (Thin)                       │
│  REST Controllers, DTOs, OpenAPI, Security Config               │
│  ▼ uses ▼                                                        │
│                   syncflow-runtime (NEW)                        │
│  Orchestration: SnapshotExecutor, SyncOrchestrator,             │
│  CaptureLifecycle, CheckpointStore, EventPublisher              │
│  ▼ uses ▼                                                        │
│                      syncflow-core                              │
│  Domain Models (Pipeline, CDCEvent, SnapshotJob),               │
│  SPI Interfaces (Connector, Writer, Repository, OffsetStore),   │
│  Pure Domain Services (validation, transformation, mapping)     │
│  ▲ implements ▲              ▲ implements ▲                     │
│  ┌─────────────────┐         ┌─────────────────┐                │
│  │ syncflow-       │         │ syncflow-       │                │
│  │ connectors      │         │ persistence     │ (NEW)          │
│  │ (Debezium, JDBC,│         │ (JPA,           │                │
│  │  Writers, etc)  │         │  repositories)  │                │
│  └─────────────────┘         └─────────────────┘                │
└──────────────────────────────────────────────────────────────────┘
          ▲
          │ uses
┌─────────┴─────────┐
│  syncflow-common  │
│  TenantContext,   │
│  Exceptions,      │
│  Base Types,      │
│  Utilities        │
└───────────────────┘
```

**Key Changes:**
1. Extract runtime orchestration to `syncflow-runtime` module
2. Add `syncflow-persistence` module for JPA repositories
3. Core becomes pure domain + SPI (no Spring, no JPA)
4. API becomes thin controller layer only

---

## 4. Refactoring Strategies

### 4.1 Immediate Fixes (P0 — Security/Correctness)

| # | Action | Files |
|---|--------|-------|
| **F1** | Replace `ThreadLocal` tenant context with `ContextPropagator` (Micrometer) or pass `TenantId` explicitly | `TenantContextHolder`, all callers in `SnapshotExecutor`, `SyncOrchestrator`, `CaptureLifecycle` |
| **F2** | Fix multi-table CDC processing in `SyncOrchestrator` | `SyncOrchestrator.start()` line 120 — iterate all `tableMappings` |
| **F3** | Implement DELETE in `DestinationRouter` / writers | `DestinationRouter.write()` case DELETE, `JdbcBatchWriter` add `deleteBatch()` |
| **F4** | Persist runtime state to DB (not in-memory maps) | Replace `ConcurrentHashMap` in `SnapshotExecutor`, `CaptureLifecycle`, `SyncOrchestrator` with JPA entities |
| **F5** | Add connection pooling to writers | `JdbcBatchWriter` → use `HikariDataSource` per connection config |

---

### 4.2 Short-term (P1 — Scalability/Performance)

| # | Action | Files |
|---|--------|-------|
| **F6** | Batch writes in `DestinationRouter` — accumulate events, flush periodically | `DestinationRouter`, `SyncOrchestrator.runInner()` |
| **F7** | Add circuit breaker + backpressure to event queues | `SyncOrchestrator.eventQueues` → use `Resilience4j` or custom |
| **F8** | Make all hardcoded constants configurable | `application.yml` + `@ConfigurationProperties` for queue size, retry policy, checkpoint interval |
| **F9** | Unify `ValidationResult` and `ProcessingContext` | Move to `syncflow-common` |
| **F10** | Extract `ConnectionConfiguration` mapper | New `ConnectionMapper` in `syncflow-common` or `syncflow-api` |

---

### 4.3 Medium-term (P2 — Architecture)

| # | Action | Modules | Status (2026-08-17) |
|---|--------|---------|----------------------|
| **F11** | Create `syncflow-runtime` module; move orchestrators out of API | New module | **Still deferred** — orchestrators stay in api; split gated on a second runtime consumer (see `docs/adr/MODULE_SPLIT_DEFERRED.md`) |
| **F12** | Create `syncflow-persistence` module; define `PipelineRepository`, `SnapshotJobRepository`, `SyncJobRepository` SPI in core | New module + core | ✅ **Done** — `syncflow-persistence` extracted; entities/repos/Flyway migrated; `PersistenceConfig`; `PipelineRepository` SPI exists in core |
| **F13** | Implement distributed locking for pipeline operations | `syncflow-runtime` + Redis/Postgres advisory locks | ✅ **Done (Postgres advisory)** — `DistributedLockService`; now also guards `SnapshotExecutor.start()` (S2) |
| **F14** | Add exactly-once CDC: transaction-aware idempotency + offset commit | `CaptureLifecycle`, `SyncOrchestrator`, `OffsetStore` | ✅ **Done** — `markProcessedIfAbsent` after write (F14 in ADR) |
| **F15** | Parallel snapshot: chunk tables, process chunks concurrently | `SnapshotExecutor`, `AbstractJdbcSnapshotConnector` | ✅ **Done** — PK-range chunking via `rangeChunks` + fixed pool `parallelism`; per-chunk checkpoints (V15) |

---

### 4.4 Long-term (P3 — Platform)

| # | Action |
|---|--------|
| **F16** | Migrate to reactive (Project Reactor) or structured concurrency for better resource control | ✅ **Done** — `StructuredTaskScope` for snapshot fan-out, `ReentrantLock` replaces all `synchronized`, `spring.threads.virtual.enabled: true`. Reactive path (WebFlux/Reactor) deferred: virtual threads + structured concurrency deliver equivalent resource control without the servlet→reactive ecosystem migration. Revisit if backpressure becomes a requirement. |
| **F17** | Add multi-region / geo-replication support |
| **F18** | Implement connector plugin system (dynamic loading) |
| **F19** | Add SQL-based transformation engine (push down to DB) |

---

## 5. Improved Production-Grade Code Samples

### 5.1 Fixed Tenant Context (No ThreadLocal)

```java
// syncflow-common/src/main/java/com/syncflow/tenant/TenantContext.java
public record TenantContext(TenantId tenantId, String userId, Map<String, String> attributes) {
    public static TenantContext system(TenantId tenantId) {
        return new TenantContext(tenantId, "system", Map.of());
    }
}

// Usage: pass explicitly, no ThreadLocal
public interface TenantAware {
    TenantId tenantId();
}

// In orchestrators:
public SnapshotJob start(String pipelineId, TenantContext ctx) { ... }
private void execute(TenantContext ctx, SnapshotJob job, PipelineDesign pipeline) { ... }
```

---

### 5.2 Batched Destination Router with Connection Pool

```java
// syncflow-api/src/main/java/com/syncflow/api/sync/BatchedDestinationRouter.java
@Component
public class BatchedDestinationRouter {

    private final Map<String, WriterPool> writerPools = new ConcurrentHashMap<>();
    private final ConnectionService connectionService;
    private final WriterRegistry writerRegistry;
    private final MeterRegistry meterRegistry;

    public WriteResult writeBatch(String connectionId, List<CDCEvent> events, List<String> destColumns) {
        var pool = writerPools.computeIfAbsent(connectionId, this::createPool);
        var writer = pool.borrow();
        try {
            var grouped = events.stream()
                    .collect(Collectors.groupingBy(e -> e.source().table()));
            
            for (var entry : grouped.entrySet()) {
                var rows = entry.getValue().stream()
                        .map(this::extractRow)
                        .filter(Objects::nonNull)
                        .toList();
                if (!rows.isEmpty()) {
                    writer.writeBatch(entry.getKey(), rows, destColumns);
                }
            }
            writer.flush();
            writer.commit();
            return new WriteResult(true, null);
        } catch (Exception e) {
            writer.rollback();
            return new WriteResult(false, e.getMessage());
        } finally {
            pool.release(writer);
        }
    }

    private WriterPool createPool(String connectionId) {
        var conn = connectionService.getWithDecryptedCredentials(connectionId);
        var config = ConnectionMapper.toConfig(conn);
        var writer = writerRegistry.get(ConnectorTypeMapper.toCore(conn.getProperties().type()))
                .orElseThrow();
        return new WriterPool(() -> {
            var w = writerRegistry.get(...).orElseThrow();
            w.connect(config);
            return w;
        }, 4); // pool size configurable
    }
}
```

---

### 5.3 Persistent Runtime State (Replaces In-Memory Maps)

```java
// syncflow-persistence/src/main/java/com/syncflow/persistence/entity/SnapshotJobEntity.java
@Entity
@Table(name = "snapshot_jobs")
public class SnapshotJobEntity {
    @Id String id;
    String tenantId;
    String pipelineId;
    @Enumerated(STRING) SnapshotStatus status;
    @Column(columnDefinition = "jsonb") String payload;
    Instant createdAt, updatedAt;
    // Indexes on tenantId, pipelineId, status
}

// syncflow-persistence/src/main/java/com/syncflow/persistence/entity/ActiveCaptureEntity.java
@Entity
@Table(name = "active_captures",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "pipeline_id"}))
public class ActiveCaptureEntity {
    @Id String id; // pipelineId
    String tenantId;
    String pipelineId;
    @Enumerated(STRING) CaptureStatus status;
    @Column(columnDefinition = "jsonb") String offset; // serialized offset map
    Instant startedAt, updatedAt;
    // Replaces CaptureLifecycle.activeCaptures ConcurrentHashMap
}
```

---

### 5.4 Multi-Table Sync Orchestrator

```java
// syncflow-runtime/src/main/java/com/syncflow/runtime/sync/MultiTableSyncOrchestrator.java
@Component
public class MultiTableSyncOrchestrator {

    public SyncJob start(String pipelineId, TenantContext ctx) {
        var pipeline = pipelineService.get(pipelineId);
        
        // One worker per table mapping (or per pipeline with partitioned queue)
        for (var tm : pipeline.tableMappings()) {
            var queue = new LinkedBlockingQueue<CDCEvent>(queueCapacity);
            var key = TenantKey.of(ctx.tenantId(), pipelineId, tm.sourceTable());
            eventQueues.put(key, queue);
            
            Thread.startVirtualThread(() -> 
                runTableWorker(ctx, pipelineId, tm, queue));
        }
    }

    private void runTableWorker(TenantContext ctx, String pipelineId,
                                TableMapping tm, BlockingQueue<CDCEvent> queue) {
        // TenantContext is passed explicitly — never set the ThreadLocal
        // on virtual-thread workers. Matches SyncOrchestrator pattern.
        try {
            var writer = batchedRouter.forTable(tm.destinationTable());
            while (running) {
                var batch = drainBatch(queue, 100);
                if (batch.isEmpty()) continue;

                var rows = batch.stream()
                    .map(this::transform)
                    .filter(Objects::nonNull)
                    .toList();

                if (!rows.isEmpty()) {
                    writer.writeBatch(tm.destinationTable(), rows, tm.columnMappings());
                }
                // Checkpoint per table
                checkpointStore.save(pipelineId, tm.sourceTable(), cursor);
            }
        } finally {
            TenantContextHolder.clear();
        }
    }
}
```

---

### 5.5 Unified ValidationResult & ProcessingContext

```java
// syncflow-common/src/main/java/com/syncflow/common/validation/ValidationResult.java
public sealed interface ValidationResult permits ValidationResult.Ok, ValidationResult.Failed {
    boolean valid();
    List<String> errors();
    
    record Ok() implements ValidationResult {
        public boolean valid() { return true; }
        public List<String> errors() { return List.of(); }
        public static Ok ok() { return new Ok(); }
    }
    
    record Failed(List<String> errors) implements ValidationResult {
        public boolean valid() { return false; }
        public static Failed failed(String... errors) { 
            return new Failed(List.of(errors)); 
        }
        public static Failed failed(List<String> errors) { 
            return new Failed(errors); 
        }
    }
}

// syncflow-common/src/main/java/com/syncflow/common/pipeline/ProcessingContext.java
public record ProcessingContext(
        PipelineDesign pipeline,
        TableMapping tableMapping,
        TenantContext tenantContext
) { }
```

---

### 5.6 Configurable Constants

```yaml
# application.yml
syncflow:
  runtime:
    snapshot:
      queue-capacity: 10000
      checkpoint-interval-batches: 5
      batch-size: 1000
      writer-pool-size: 4
    cdc:
      queue-capacity: 10000
      max-retries: 3
      base-retry-delay-ms: 1000
      offset-flush-interval-ms: 5000
    sync:
      queue-capacity: 10000
      poll-timeout-ms: 500
      max-retries: 3
      base-retry-delay-ms: 1000
      writer-batch-size: 100
      writer-flush-interval-ms: 100
```

```java
@ConfigurationProperties("syncflow.runtime")
public class RuntimeProperties {
    private Snapshot snapshot = new Snapshot();
    private Cdc cdc = new Cdc();
    private Sync sync = new Sync();
    
    // nested classes with defaults...
}
```

---

## 6. Optimization Summary

| Area | Current | Target | Effort |
|------|---------|--------|--------|
| **Tenant isolation** | ThreadLocal (mitigated) — explicit passing + worker assertions | Full ThreadLocal removal | Low |
| **Multi-table support** | First table only | Full pipeline parallelism | Medium |
| **DELETE propagation** | Not implemented | Full CRUD sync | Low |
| **Connection management** | Per-event new connection | Pooled, batched | Medium |
| **Runtime state** | In-memory maps | Persistent (DB) + distributed lock | High |
| **Exactly-once CDC** | At-least-once | Transactional idempotency | High |
| **Horizontal scaling** | Single JVM | Multi-pod with shared state | High |
| **Configuration** | Hardcoded | Externalized + validated | Low |
| **Code duplication** | 5+ Connection mappers, 2 ValidationResults | Single shared utilities | Low |

---

## 7. Recommended Implementation Order

> **Status (2026-08-17):** items 1–4 and item 5 are complete. F15 (parallel
> snapshot) is also done. Remaining roadmap below.

1. ~~**Week 1-2**: F1, F2, F3, F9, F10 (correctness + deduplication)~~ ✅ done
2. ~~**Week 3-4**: F5, F6, F8 (performance + config)~~ ✅ done
3. ~~**Week 5-6**: F4, F7 (persistence + resilience)~~ ✅ done (runtime state durable, backpressure via DLQ)
4. ~~**Week 7-8**: F11, F12 (architecture extraction)~~ ✅ persistence extracted; `syncflow-runtime` still deferred (see ADR)
5. ~~**Week 9-10**: F13, F14 (distributed + exactly-once)~~ ✅ done (Postgres advisory locks, mark-after-write idempotency)
6. **Ongoing**: F15 ✅ done (parallel PK-range chunking); F16 ✅ done (structured concurrency: `StructuredTaskScope`, `ReentrantLock`, virtual threads); F17+ (geo-replication, plugin system, SQL-transform pushdown) still open

---

*This analysis is based on code review as of 2026-08-12. No changes were made to the codebase.*