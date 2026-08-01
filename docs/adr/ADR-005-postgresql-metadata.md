# ADR-005: Why PostgreSQL for Metadata?

## Status: Accepted

## Context
SyncFlow needs a metadata store to persist pipeline definitions, connection configurations, audit logs, workflow state, and checkpoints. Options included PostgreSQL, MySQL, MongoDB, and embedded H2.

## Decision
Use PostgreSQL as the primary metadata store. Flyway for migrations. H2 for development/testing only.

## Rationale
- **ACID compliance**: Pipeline state transitions and audit records require transactional guarantees. PostgreSQL's serializable isolation prevents race conditions on concurrent pipeline updates.
- **JSONB support**: Connection configurations, pipeline mappings, and transformation rules are stored as JSONB documents. PostgreSQL can index JSONB and run `jsonb_path_exists` queries — no separate document database needed.
- **Mature migration tooling**: Flyway's versioned migrations integrate with CI/CD pipelines — rollbacks are documented, forward migrations are idempotent.
- **Ecosystem integration**: Compatibility with Debezium (for CDC), pgvector (for AI embeddings), and Postgres extensions supports future use cases without introducing new database engines.

## Consequences
- A dedicated PostgreSQL deployment (or RDS/Aurora for production) is required.
- Migrations are forward-compatible for metadata storage — new columns do not affect running versions.
- Connection pool managed by HikariCP at 10 connections per deployment.

## Links
- `V1__init.sql`, `V2__connections.sql`
- `application.yml`: `spring.datasource.url: jdbc:postgresql://localhost:5432/syncflow`
