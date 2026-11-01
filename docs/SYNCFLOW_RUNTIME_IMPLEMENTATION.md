# syncflow-runtime implementation

**Status:** In progress  
**Owner:** SyncFlow architecture  
**Scope:** Extract orchestration from `syncflow-api` without reversing hexagonal dependencies.

## Context

`syncflow-api` currently contains REST controllers, Spring configuration, Kafka/SSE adapters, persistence-facing services, and runtime orchestration. The deferred `syncflow-runtime` split is now being implemented incrementally. The runtime module must remain usable by a future worker or CLI without importing API/web infrastructure.

## Dependency rules

```text
syncflow-common       <- core <- connectors
                         ^       ^
                         |       |
                    persistence
                         ^
                         |
api -> runtime -> core/common
api -> persistence/connectors/security/monitoring/plugin-api
```

The arrows represent compile-time dependencies. `syncflow-core` must not depend on `syncflow-api` or `syncflow-runtime`. `syncflow-runtime` must not depend on `syncflow-api`, `syncflow-persistence`, Spring Web, Spring Data/JPA, Kafka clients, or `SseEmitter`.

### Runtime owns

- CDC/sync orchestration and batching.
- Snapshot execution and worker coordination.
- Retry policy and lifecycle coordination.
- Calls to core connector, writer, pipeline, and CDC ports.

### API owns

- REST controllers and DTOs.
- Spring composition and transactions.
- JPA repositories/entities and persistence adapters.
- Kafka clients/topic provisioning.
- SSE transport and `SseEmitter`.
- Encryption, request tenant resolution, metrics/configuration binding.

## Ports and adapters

Only introduce a port when an extracted runtime class has an existing concrete caller and adapter:

- Pipeline lookup: tenant-explicit lookup of `PipelineDesign`.
- Connection lookup: tenant-explicit lookup of decrypted `ConnectionConfiguration`.
- Runtime status sink: status events without SSE types.
- Runtime lock: named lock acquisition/release.
- Runtime state ports: sync, snapshot, capture, checkpoint, offset, idempotency, DLQ, and queue snapshots where needed.
- CDC publisher/consumer lifecycle: Kafka remains an API adapter.

Use existing core ports and types rather than duplicating them: `ConnectorRegistry`, `WriterRegistry`, `SnapshotCapableConnector`, `CdcCapableConnector`, `DestinationWriter`, `EventPublisher`, `PipelineDesign`, `CDCEvent`, `SyncJob`, `SnapshotJob`, and `TenantContext`.

Every runtime worker receives `TenantContext` explicitly. It must not restore `TenantContextHolder` across an async boundary.

## Migration order

1. Add and compile the module shell with no API or persistence dependency.
2. Add architecture rules for core/runtime/API direction.
3. Add tenant-explicit pipeline and connection lookup boundaries.
4. Move low-edge orchestration first: `DestinationRouter`, `RetryEngine`, and `SnapshotWorker`.
5. Move `SnapshotExecutor` and `SyncOrchestrator` behind constructor-injected ports.
6. Move `CaptureLifecycle` only after CDC publisher/consumer lifecycle ports exist.
7. Construct runtime services once from API configuration; do not rely on component scanning.
8. Keep JPA/Kafka/SSE implementations in API adapters until a second consumer justifies a separate SPI module.

## Source ownership during migration

The first extraction keeps persistence-backed helpers (`DeadLetterQueue`, `EventIdempotencyStore`, `CheckpointStore`, and `OffsetStore`) in API as adapter implementations. They implement runtime ports or are wrapped by API adapter classes. `DistributedLockService`, `RuntimeProperties`, `RuntimeStateJson`, `MetricsHelper`, `StatusBroadcaster`, Kafka classes, encryption, and controllers remain API-owned.

Runtime classes are plain Java services. They must not carry `@Component`, `@Transactional`, `@ConfigurationProperties`, Spring Web, JPA, Kafka, or SSE dependencies.

## Verification gates

```bash
./gradlew :syncflow-runtime:compileJava
./gradlew :syncflow-runtime:test
./gradlew :syncflow-core:test :syncflow-runtime:test
./gradlew :syncflow-api:test
./gradlew test
```

The architecture tests must verify:

- core has no dependency on API or runtime;
- runtime has no dependency on API or persistence;
- runtime does not reference Spring Web, JPA entities/repositories, Kafka clients, or `SseEmitter`;
- the application context creates exactly one runtime service instance after wiring.

The API integration and contract suites must continue to verify unchanged REST routes, HTTP status/DTO shapes, SSE keys, Kafka topic names, tenant scoping, retry behavior, idempotency, snapshot checkpoints, and Flyway schema.

## Rollback boundary

Each class move is a separate commit-sized change. If an extracted class still needs an API type, stop at the port boundary instead of adding a reverse dependency. Restore the previous API implementation until the adapter contract is explicit and covered by tests.

## Completion criteria

The extraction is complete when `syncflow-runtime` compiles independently, forbidden dependency checks pass, runtime unit tests pass, API context tests pass, and full API integration behavior is unchanged. Update `docs/adr/MODULE_SPLIT_DEFERRED.md` and `ARCHITECTURE_ANALYSIS.md` only after those gates pass.
