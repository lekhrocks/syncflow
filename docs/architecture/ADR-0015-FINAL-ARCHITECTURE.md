# ADR-0015: Final Production Architecture

## Status: Accepted

## Context
SyncFlow has evolved through 15 implementation phases into a complete enterprise data synchronization platform. This ADR finalizes the production architecture.

## Decisions

### 1. Module Architecture
- 17 modules across 3 layers: SDK, Core, Application/Infrastructure
- No circular dependencies between modules
- Plugin SDK has zero dependencies on platform internals

### 2. Deployment Topology
- Multi-region active-active with regional ingress
- Pod topology spread across availability zones
- KEDA autoscaling based on queue depth and CPU
- Leader election via lease coordination for scheduler

### 3. Data Model
- Single-database multi-tenant with tenant_id isolation
- Database-per-tenant upgrade path available without schema changes
- Immutable audit records with GDPR right-to-delete

### 4. Security Model
- OIDC/OAuth2/JWT identity layer
- Resource-based authorization (19 permission types across 7 roles)
- AES-256 encryption for secrets at rest
- mTLS for control plane → agent communication
- API key hashing (SHA-256) — raw keys never persisted

### 5. AI Architecture  
- Multi-agent orchestration with 8 specialized agents
- Read-only tool registry — AI never executes operations
- RAG knowledge base with keyword retrieval

### 6. Observability
- OpenTelemetry tracing via OTLP collector
- Prometheus metrics with 15 alert rules
- Structured JSON logging with trace/correlation/pipeline IDs
- SLO tracking (99.9% API availability, 99.5% pipeline success)

## Consequences
Positive: Clear separation of concerns, scalable across regions, secure by default.
Negative: Initial complexity for single-region deployments (mitigated via default values).
