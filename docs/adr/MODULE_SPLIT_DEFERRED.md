# Module Architecture Decision

## Current state (2026-08-12)

`syncflow-api` contains orchestration classes (`SyncOrchestrator`, `SnapshotExecutor`,
`CaptureLifecycle`, `DestinationRouter`) plus all JPA entities and repositories.

The original architecture analysis recommended extracting these into two
separate modules:
- `syncflow-runtime` — orchestrators
- `syncflow-persistence` — JPA entities + repositories

## Status (2026-08-17): persistence extracted; runtime split still deferred

A module split is **not done** in this batch. Reasons:

1. **Cost / benefit**: the API module is currently the only Spring Boot
   application. Splitting requires moving ~30 classes, rewriting build.gradle
   dependencies, and updating tests. The benefit is only realized when a
   second consumer of the runtime exists (e.g. a CLI runner or a worker
   binary). Today: one consumer, one module.

2. **Risk**: module splits break tests that mock JPA repositories. The
   current test suite has 490 passing tests; a split will require updating
   many of them and may regress.

3. **Alternative marker**: the JPA-related types live in
   `com.syncflow.api.{cdc,sync}.entity` packages (e.g.
   `ActiveCaptureEntity`, `SyncJobEntity`). If the module split ever lands,
   these packages are the natural "persistence" boundary — moving them
   becomes mechanical.

## What IS done in this batch

- New `ActiveCaptureEntity` + `ActiveCaptureRepository` to durably persist
  CDC capture state (F4)
- `DistributedLockService` for multi-pod coordination (F13)
- `markProcessedIfAbsent` for exactly-once CDC semantics (F14)
- `TenantContext` flows explicitly through every orchestrator method (F1)
- `RuntimeProperties` externalized (F8)
- `ConnectionMapper` extracted (F10)
- `PooledJdbcBatchWriter` with HikariCP (F5)
- Batched writes via `DestinationRouter.writeBatch` (F6)
- Backpressure: queue-full → DLQ (F7)
- SQL identifier sanitization (C1)
- Unified SPI parameter order (F3)
- Multi-table CDC dispatch with observability (F2)

## Added in this follow-up batch (2026-08-17)

- **`syncflow-persistence` module extracted** (F11/F12, the persistence half):
  all JPA entities + Spring Data repositories + Flyway migrations
  (`V1`–`V15`) moved out of `syncflow-api`, per-domain subpackages preserved.
  `api` now depends on `:syncflow-persistence` and lists the formerly
  transitive deps explicitly (`spring-tx`, `spring-data-commons`,
  `spring-data-jpa`, `spring-jdbc`).
  `PersistenceConfig` (`@EntityScan`/`@EnableJpaRepositories`) wires the new
  packages; root app still scans `com.syncflow`.
- **Snapshot start guard** (S2): `SnapshotExecutor.start()` now holds the
  Postgres advisory lock (`snapshot:<pipeline>`) and returns an existing
  RUNNING job instead of spawning a second worker.
- **Parallel PK-range snapshot** (F15): `SnapshotCapableConnector.rangeChunks`
  splits a numeric-PK table into disjoint `[start,end)` ranges; the executor
  processes all (table, chunk) work items on a fixed pool sized by
  `syncflow.runtime.snapshot.parallelism` (default 4), writes serialized on a
  single `DestinationWriter`. Per-chunk resume checkpoints
  (`V15__snapshot_chunk_checkpoints.sql` adds `chunk_index`).
  Non-numeric single-column PKs (uuid/text/date) fall back to sequential.
- **Keyset cursor typed binding** (P6): numeric cursors now bind as `Long`,
  fixing `bigint >= character varying` for both chunked and sequential paths.
- Residual in-memory state (`cancellations`, `eventQueues`, `runningFlags`) is
  intentionally ephemeral (cancellation flags + live queues); the durable shape
  (jobs, status, statistics, checkpoints) is all in Postgres.

## When to revisit the module split

Triggers that justify the cost:

- A second runtime consumer (CLI / worker binary / embedded library)
- Independent release cadence for runtime vs API
- Build time becomes dominated by orchestrator recompilation
- A clear ownership boundary between runtime team and persistence team

Until one of those, the current single-module layout with package-level
separation is the right trade-off.