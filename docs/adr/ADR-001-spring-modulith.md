# ADR-001: Why Spring Modulith?

## Status: Accepted

## Context
SyncFlow is a modular platform with multiple bounded contexts (connections, pipelines, metadata, CDC, sync, monitoring, AI). We needed a module system that enforces boundaries at compile time without the overhead of full microservices.

## Decision
Use Spring Modulith as the module architecture framework.

## Rationale
- **Compile-time module boundaries**: Prevents circular dependencies between modules (core → api, never api → core) enforced by ArchUnit tests.
- **Event-driven communication**: Modules communicate through `ApplicationEventPublisher` rather than direct bean injection — preserves the hexagonal architecture.
- **Testing simplicity**: Single JVM for integration tests (Testcontainers) vs. orchestrating multiple microservices.
- **Future extraction path**: Modulith modules can be extracted to separate services if needed — the event contract is already there.

## Consequences
- All existing integration tests run in a single Spring context — fast CI feedback.
- Modules can be independently versioned via Gradle subprojects.
- ArchUnit tests verify no illegal module dependencies.

## Links
- Architecture test: `ArchitectureTest.java`
