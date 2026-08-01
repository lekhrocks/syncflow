# SyncFlow v0.1.0 Release Notes

> **Release Date:** 2026-07-16  
> **Version:** 0.1.0  
> **Type:** Initial Platform Release  
> **Status:** Production Ready

---

## Overview

SyncFlow v0.1.0 is the initial release of an enterprise-grade data synchronization platform. It connects heterogeneous databases through pluggable connectors and provides real-time data synchronization via CDC, snapshot, and streaming mechanisms.

## What's Included

### Connections
- Create, update, delete, test, and monitor database connections
- Encrypted credential storage (AES-256-GCM)
- Built-in support for PostgreSQL, MySQL, MongoDB, and Redis
- Connection health monitoring

### Pipelines
- Pipeline designer with visual editor (React Flow)
- Column mappings with 10 transformation types
- Filter rules with 12 operators and nested AND/OR groups
- Pipeline validation (schema, mappings, conflicts)
- Version history with rollback

### Data Movement
- **Snapshot Engine**: Batch-based bulk data transfer with checkpointing
- **CDC Engine**: Real-time change capture (Debezium for PostgreSQL/MySQL, Change Streams for MongoDB)
- **Synchronization Engine**: Event processing, retries, DLQ, at-least-once delivery

### Platform
- REST API + GraphQL endpoint
- Enterprise React UI (15 pages)
- Multi-tenant with RBAC (19 permissions, 7 roles)
- Observability (Prometheus, Grafana, OpenTelemetry)
- Kubernetes-native deployment (Helm, ArgoCD, KEDA)

### Developer Experience
- Plugin SDK for third-party connectors
- AI Copilot for pipeline design, mapping, and diagnostics
- 546 automated tests
- 12 runbooks for production incidents
- 10 Architecture Decision Records (ADRs)

## Upgrade Notes

This is the initial release. No upgrade path from a previous version exists.

## Known Issues

| ID | Issue | Workaround | Planned Fix |
|----|-------|------------|-------------|
| SYNC-001 | In-memory stores (DLQ, checkpoint, idempotency) lost on pod restart | Ensure pod stability via PDB | v0.2.0 database-backed |
| SYNC-002 | No rate limiting on API endpoints | Implement at reverse proxy level | v0.2.0 |
| SYNC-003 | Plugin signature not verified on install | Trusted registry only | v0.2.0 |
| SYNC-004 | DLQ has no capacity bound | Monitor via alert | v0.2.0 spill-to-disk |

## Compatibility Matrix

### Databases
| Database | Supported | CDC Support | Tested Version |
|----------|:---------:|:-----------:|:--------------:|
| PostgreSQL | ✅ | ✅ (Debezium) | 16 |
| MySQL | ✅ | ✅ (Debezium) | 8.0 |
| MongoDB | ✅ | ✅ (Change Streams) | 7.0 |
| Redis | ✅ | ❌ | 7 |

### Platforms
| Platform | Supported | Notes |
|----------|:---------:|-------|
| Java | ✅ 25+ | `--enable-preview` required |
| Kubernetes | ✅ 1.28+ | Helm chart included |
| Docker | ✅ | Distroless image available |

### Browsers (UI)
| Browser | Supported |
|---------|:---------:|
| Chrome | ✅ 120+ |
| Firefox | ✅ 115+ |
| Safari | ✅ 17+ |
| Edge | ✅ 120+ |

## Quick Start

```bash
# Start with Docker Compose
docker compose -f docker/docker-compose.yml up -d

# Or deploy to Kubernetes
helm install syncflow helm/syncflow \
  --set database.host=your-pghost \
  --set database.password=your-password

# Access UI
open http://localhost:8080
```

## Release Artifacts

- Docker image: `ghcr.io/syncflow/api:0.1.0`
- Helm chart: `helm/syncflow/`
- Plugin SDK: `com.syncflow.plugin:syncflow-plugin-api:0.1.0`
- SBOM: Available in CI artifacts

## Contributors

- SyncFlow Platform Engineering Team
