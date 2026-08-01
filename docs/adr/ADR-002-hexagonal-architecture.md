# ADR-002: Why Hexagonal Architecture?

## Status: Accepted

## Context
SyncFlow must support multiple database connectors (PostgreSQL, MySQL, MongoDB, Redis), multiple transport protocols (REST, GraphQL), and multiple deployment modes (CLI, embedded, agent). The core domain logic must never depend on infrastructure.

## Decision
Use Hexagonal (Ports & Adapters) Architecture with strict dependency rules:
- `syncflow-core` — domain model, SPI interfaces (ports). Zero framework dependencies beyond SLF4J.
- `syncflow-connectors` — JDBC/MongoDB/Redis implementations (adapters). Implements SPI.
- `syncflow-api` — REST controllers, GraphQL resolvers, Spring configuration. Outer ring.
- `syncflow-plugin-api` — independent SPI for third-party connectors.

## Rationale
- **Testability**: Domain logic tested without Spring Boot (pure JUnit 5) — 33 core domain tests run in milliseconds.
- **Connector isolation**: Adding MongoDB support required zero changes to the CDC engine — just a new adapter class.
- **Plugin readiness**: The `syncflow-plugin-api` module provides the same SPI contracts as the internal connectors, but published as a standalone artifact.

## Consequences
- `syncflow-core` has no Spring Boot dependency — only `spring-context` for `@Component` scanning.
- All database access goes through SPI interfaces — never direct JDBC in domain code.
- Infra concerns (Flyway, REST, security) are strictly in `syncflow-api`.

## Links
- Module dependency validated by `ArchitectureTest.coreShouldNotDependOnApi()`
