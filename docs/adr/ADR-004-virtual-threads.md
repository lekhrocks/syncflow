# ADR-004: Why Virtual Threads?

## Status: Accepted

## Context
SyncFlow executes concurrent operations: multiple snapshot batches, parallel CDC connectors, simultaneous sync workers. Traditional thread-per-request with a fixed thread pool requires careful sizing and often leads to thread starvation under load.

## Decision
Use JDK 25 Virtual Threads (`Thread.startVirtualThread()`) for all concurrent execution paths. Structured concurrency (`StructuredTaskScope`) for fan-out workloads. `ReentrantLock` instead of `synchronized` to avoid carrier-thread pinning.

## Usage in SyncFlow
- **Snapshot engine**: Parallel chunk fan-out uses `StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())` — one forked task per work item, structured lifecycle, automatic shutdown on failure (`SnapshotExecutor.java`).
- **CDC engine**: Debezium engine runs on a virtual thread (`DebeziumCdcConnector.java`).
- **CDC (MongoDB)**: Change Streams cursor runs on a virtual thread (`MongoDbCdcConnector.java`).
- **Sync orchestrator**: Each pipeline's event consumer runs on a virtual thread (`SyncOrchestrator.java`).
- **Tomcat**: `spring.threads.virtual.enabled: true` handles HTTP requests on virtual threads.

## Rationale
- **No thread pool sizing**: Virtual threads are cheap (~1KB stack) — start as many as needed.
- **Blocking I/O is fine**: JDBC calls, HTTP requests, and Kafka client calls all release the underlying carrier thread while waiting.
- **Structured concurrency**: `StructuredTaskScope` replaces manual thread-join + work-queue patterns with structured lifecycle management and automatic failure propagation.
- **Simple code**: No `CompletableFuture` chaining — synchronous code on virtual threads is easier to read and debug.

## Consequences
- All blocking operations must go through virtual-thread-aware APIs (JDBC, HTTP client, etc.).
- `synchronized` blocks replaced with `ReentrantLock` to avoid pinning carrier threads (`SnapshotExecutor`, `SnapshotWorker`, `RegionalDataSourceFactory`).
- `StructuredTaskScope` is final in JDK 25 (was incubating in `jdk.incubator.concurrent` through JDK 24).

## Links
- `spring.threads.virtual.enabled: true` in `application.yml`
- `SnapshotExecutor.java` — `StructuredTaskScope` fan-out
- `SnapshotWorker.java` — `ReentrantLock` for writer/progress serialization
- `RegionalDataSourceFactory.java` — `ReentrantLock` for failover
