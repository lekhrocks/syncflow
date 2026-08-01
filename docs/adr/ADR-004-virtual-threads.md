# ADR-004: Why Virtual Threads?

## Status: Accepted

## Context
SyncFlow executes concurrent operations: multiple snapshot batches, parallel CDC connectors, simultaneous sync workers. Traditional thread-per-request with a fixed thread pool requires careful sizing and often leads to thread starvation under load.

## Decision
Use JDK 25 Virtual Threads (`Thread.startVirtualThread()`) for all concurrent execution paths.

## Usage in SyncFlow
- **Snapshot engine**: Each snapshot runs on its own virtual thread (`SnapshotExecutor.java:134`).
- **CDC engine**: Debezium engine runs on a virtual thread (`DebeziumCdcConnector.java:175`).
- **CDC (MongoDB)**: Change Streams cursor runs on a virtual thread (`MongoDbCdcConnector.java:105`).
- **Sync orchestrator**: Each pipeline's event consumer runs on a virtual thread (`SyncOrchestrator.java:105`).

## Rationale
- **No thread pool sizing**: Virtual threads are cheap (~1KB stack) — start as many as needed.
- **Blocking I/O is fine**: JDBC calls, HTTP requests, and Kafka client calls all release the underlying carrier thread while waiting.
- **Simple code**: No `CompletableFuture` chaining — synchronous code on virtual threads is easier to read and debug.
- **Future-proof**: JDK 25 virtual threads are mature (incubated since JDK 19, final in JDK 21).

## Consequences
- All blocking operations must go through virtual-thread-aware APIs (JDBC, HTTP client, etc.).
- `synchronized` blocks should be replaced with `ReentrantLock` where they might pin carrier threads.
- Thread pool executors are not needed — `Executors.newVirtualThreadPerTaskExecutor()` replaces them.

## Links
- `spring.threads.virtual.enabled: true` in `application.yml`
