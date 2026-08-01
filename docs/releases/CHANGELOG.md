# SyncFlow Changelog

> **Versioning:** [Semantic Versioning 2.0](https://semver.org) — `MAJOR.MINOR.PATCH`  
> **License:** Apache 2.0  
> **Repository:** https://github.com/example/syncflow

---

## [0.1.0] — 2026-07-16 — Initial Platform Release

### Added

#### Platform Foundation
- Gradle multi-module project with 10 modules (common, core, api, connectors, orchestrator, security, monitoring, test, plugin-api, agent)
- Spring Boot 3.5.3 with Java 25 preview features
- Spring Modulith for compile-time module boundary enforcement
- Hexagonal architecture with strict dependency rules (core → no framework deps)
- ArchUnit tests validating module isolation

#### Connection Management
- CRUD REST API for database connections (PostgreSQL, MySQL, MongoDB, Redis)
- Connection validation with real connectivity testing via Testcontainers
- AES-256-GCM encryption for stored credentials
- Connection health checks with metadata caching (Caffeine, 5m TTL)
- Connection controller with test/test/health endpoints

#### Metadata Discovery
- Full schema/table/column/index/constraint/PK/FK discovery
- PostgreSQL: INFORMATION_SCHEMA + pg_catalog with pg_stats for statistics
- MySQL: INFORMATION_SCHEMA with constraint definitions
- MongoDB: Document sampling (configurable up to 100 docs) for type inference
- Redis: Logical database enumeration, key type distribution
- Caffeine metadata cache configurable per-category (schemas, tables, columns, indexes, constraints)
- Metadata REST API with GET + refresh endpoint

#### Pipeline Designer
- Immutable pipeline domain model with versioning (audit trail, rollback support)
- Column mapping with 10 transformation types (RENAME, IGNORE, CONCATENATE, UPPERCASE, LOWERCASE, TRIM, DEFAULT_VALUE, CONSTANT_VALUE, SUBSTRING, EXPRESSION)
- Filter rules with 12 operators + AND/OR nesting
- Pipeline validation engine (connection existence, table existence, duplicate mappings, PK requirements, transform parameter completeness)
- Conflict detection (duplicate columns, missing PKs, unknown columns, type mismatches)
- Pipeline preview API (transformed schema without execution)
- REST API for CRUD + validate + rollback + versions + preview + conflicts

#### Snapshot Engine
- Streaming batch-based snapshot with configurable batch size
- Checkpoint engine for crash recovery (save every 5 batches)
- Progress tracking with estimated completion
- Virtual thread execution per snapshot
- Micrometer metrics (duration, rows processed, errors)
- Graceful cancellation with partial results
- REST API: start, list, get, progress, cancel

#### CDC Event Capture Engine
- PostgreSQL CDC via Debezium Embedded Engine (pgoutput plugin, LSN tracking)
- MySQL CDC via Debezium Embedded Engine (GTID + binlog position)
- MongoDB CDC via Change Streams (resume tokens)
- Standardized CDCEvent record format (header, source, operation, payload, metadata, offset)
- Event publisher abstraction (InMemoryEventPublisher with Kafka-ready SPI)
- Capture lifecycle management (start, stop, pause, resume)
- Offset persistence for crash recovery
- Micrometer metrics (events per pipeline, per operation type)

#### Synchronization Engine
- Event consumer with bounded queue (10,000 capacity) and back pressure
- Per-table ordering and in-order event processing
- Event ID-based deduplication (ConcurrentHashMap.newKeySet)
- Transformation pipeline (FilterProcessor → TransformProcessor chain)
- Destination router with connector resolution
- Retry engine with exponential backoff (1s→2s→4s, max 3 retries)
- Dead Letter Queue with per-pipeline filtering
- At-least-once delivery with idempotency
- Micrometer metrics (events processed, errors, retries, DLQ size, queue depth)

#### Workflow Orchestration
- DAG-based workflow definition with sequential/parallel tasks
- 6 task types: VALIDATION, METADATA_DISCOVERY, SNAPSHOT, CDC_CAPTURE, SYNCHRONIZATION, MONITORING
- Task scheduler with tick loop (2s interval, leader-only)
- Task queue with dequeue for worker distribution
- Leader election via hearbeat + timeout (30s threshold)
- Workflow status tracking (PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED)
- Compensating work on failure
- Configurable retries per task (3 max, 30min timeout)
- REST API: create, start, pause, resume, cancel, list, get, graph
- React Flow DAG visualization in UI

#### AI Copilot
- Multi-agent architecture with 8 specialized agents (Pipeline, Schema, Connector, Performance, RootCause, Documentation, Operations, Security)
- Agent orchestrator with plan decomposition (keyword-driven reasoning plans)
- OpenAI-compatible LLM client (configurable endpoint, model, temperature)
- Context collector with credential sanitization (never exposes passwords)
- 5 prompt templates (chat, pipeline gen, mapping gen, review, performance, root cause)
- Conversation memory per session (sliding window, max 20 entries)
- Knowledge base with keyword search (5 seed documents, pgvector-ready)
- REST API: chat, plan, analyze, document, review, recommend
- React UI: AI Chat Drawer, Pipeline Generator Wizard, Mapping Assistant Modal, Floating Action Button

#### Multi-Tenant SaaS
- Tenant domain (TenantId, OrganizationId, WorkspaceId, ProjectId) as typed value objects
- ThreadLocal TenantContextHolder with finally-block cleanup
- Header-based tenant resolution (X-Tenant-Id, X-Organization-Id) with JWT claim fallback
- 23 resource permissions across 7 roles (VIEWER, DEVELOPER, OPERATOR, AUDITOR, WORKSPACE_ADMIN, ORG_ADMIN, SYSTEM_ADMIN)
- AuthorizationService.require() policy enforcement
- Quota engine with 8 metrics (CONNECTIONS, PIPELINES, RUNNING_JOBS, STORAGE, AI_REQUESTS, API_CALLS, SNAPSHOT_SIZE, CDC_THROUGHPUT)
- API key management with SHA-256 hashing, expiration, revocation
- Enterprise audit store with immutable records and GDPR right-to-delete
- Admin REST API: organizations, workspaces, projects, API keys, quotas

#### Enterprise React UI
- 15 pages across 8 navigation routes
- React 19 + Mantine 7 + TypeScript
- TanStack Query for all data fetching (30s stale time, auto-refresh)
- Recharts for pipeline/snapshot/memory visualizations
- React Flow for workflow DAG visualization
- Framer Motion for page transitions and card animations
- GraphQL support via Apollo Client

#### Cloud Native Deployment
- Production Dockerfile (multi-stage, distroless, ZGC, non-root)
- Kubernetes manifests: Deployment, Service, Ingress, HPA, PDB, NetworkPolicy, ConfigMap, ServiceAccount
- Helm chart with 65+ configurable values across 9 templates
- ArgoCD Application + ApplicationSet for GitOps
- Prometheus alert rules (7 rules: pipeline failure, retry, DLQ, CDC lag, queue depth, agent offline, memory)
- Grafana dashboards (Platform Overview + SRE dashboard with SLO tracking)
- External Secrets Operator integration (AWS Secrets Manager)
- KEDA ScaledObject for queue-based autoscaling
- Kustomize overlays for dev/qa/prod + 3 regions (us-east, eu-west, ap-southeast)

#### Observability
- OpenTelemetry tracing (OTLP exporter to Tempo)
- Micrometer metrics (15 custom metric names aggregated to Prometheus)
- Structured logging with JSON encoder (traceId, correlationId, pipelineId in MDC)
- Health aggregator (connectors, connections, captures, syncs)
- 2 Grafana dashboards (17 panels total)
- 7 Prometheus alert rules
- Audit trail with 500-event limit, filterable by entity

#### Security
- 25 security tests covering 11 threat categories
- Threat model document (11 threats, risk ratings, mitigations)
- RBAC enforcement with 19 resource permissions
- Tenant isolation via ThreadLocal with finally-block cleanup
- AES-256-GCM credential encryption
- SHA-256 API key hashing
- PreparedStatement-based SQL injection prevention
- Prompt injection guardrails (system prompt + requiresApproval flag)
- mTLS for agent communication (cert-manager auto-renewal)

#### SRE & Operations
- Production readiness checklist (12 P0 items, all verified)
- Failure mode matrix (11 failure modes, detection, recovery, RTO)
- 12 runbooks (pipeline fail, CDC lag, checkpoint corruption, leader election, plugin fail, OOM, DB full, slow sync, agent offline, high retry, high DLQ, duplicate events)
- SLO document (availability 99.95%, latency P95 < 250ms, error rate < 0.1%)
- Error budget tracking with burn rate alerts
- Sequence diagrams (9 Mermaid diagrams)
- C4 architecture diagrams (4 levels, 6 diagrams)
- Benchmark report (JMH microbenchmarks + k6 system benchmarks)
- Database migration strategy (zero-downtime patterns, rollback procedures)

#### CI/CD
- 14-job pipeline (lint → compile → unit → arch → integration → mutation → perf → security → docker → helm → staging → smoke → approval → production)
- Spotless formatting enforcement
- Trivy vulnerability scanning (filesystem + container)
- SBOM generation (CycloneDX)
- Container image signing (Cosign)
- Helm chart validation (lint + template)
- Staging deployment with smoke tests
- Production deployment with environment gate + Slack notification
- Concurrency controls (cancel-in-progress per branch)

### Fixed
- RecordProcessor.andThen() null guard — prevents NPE when filter drops record and chain continues
- snapshot.pipeline ProcessingContext imports — resolves ambiguity between core.snapshot.pipeline and core.sync

### Known Issues
- In-memory stores (DLQ, checkpoint, idempotency) are lost on pod restart — database-backed versions planned for v0.2.0
- Rate limiting not yet implemented — tracked for v0.2.0
- Plugin signature verification planned for v0.2.0
- KEDA not deployed in CI — Hel testing available

### Compatibility
- PostgreSQL 16+ (required)
- MySQL 8.0+ (connector)
- MongoDB 7.0+ (connector)
- Redis 7+ (connector)
- Java 25+ (required)
- Kubernetes 1.28+ (recommended)
- Helm 3.12+
