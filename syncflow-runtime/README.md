# syncflow-runtime

Framework-free orchestration module for SyncFlow. Runtime code depends on `syncflow-core` and `syncflow-common` only.

## Allowed dependencies

- `syncflow-core` domain types and ports.
- `syncflow-common` tenant context and shared primitives.
- SLF4J logging.

## Forbidden dependencies

Runtime must not depend on `syncflow-api`, `syncflow-persistence`, Spring Web/Data, JPA entities/repositories, Kafka clients, or `SseEmitter`.

API supplies adapters for tenant-scoped connection/pipeline lookup, persistence, locks, metrics, Kafka, and SSE. Runtime receives those capabilities through explicit ports and `TenantContext` parameters.

## Verification

```bash
./gradlew :syncflow-runtime:compileJava :syncflow-runtime:test
```

Architecture rules in `syncflow-api/src/test/java/com/syncflow/api/ArchitectureTest.java` reject runtime-to-API and runtime-to-persistence dependencies.
