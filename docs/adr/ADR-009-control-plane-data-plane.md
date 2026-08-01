# ADR-009: Why Control Plane / Data Plane Separation?

## Status: Accepted

## Context
Enterprise customers require SyncFlow to run inside their VPC while being managed from a centralized cloud dashboard. The alternative is a fully on-premise installation (no cloud management).

## Decision
Separate SyncFlow into a Control Plane (cloud-hosted, multi-tenant) and Data Plane (customer-deployed agents). The control plane never touches customer databases directly.

## Architecture
```
Customer VPC              SyncFlow Cloud
┌─────────────┐           ┌──────────────────┐
│ Data Plane  │  HTTPS    │  Control Plane   │
│ Agent       │ ←─────── →│  Fleet Manager   │
│ ┌─────────┐ │  mTLS     │  Pipeline Mgmt   │
│ │Snapshot │ │           │  User/Org Mgmt   │
│ │CDC      │ │           │  Monitoring      │
│ │Sync     │ │           │  AI Copilot      │
│ │Metadata │ │           │  Dashboard UI    │
│ └─────────┘ │           └──────────────────┘
│  PostgreSQL │
│  MongoDB    │
│  MySQL      │
└─────────────┘
```

## Rationale
- **Security**: Customer credentials never leave the VPC. The control plane only receives metadata, metrics, and health status.
- **Resilience**: Agents continue running if the control plane is unreachable — work queues are local, checkpoints persist.
- **Scalability**: Control plane scales independently of data plane. 1000 agents can report to a single control plane.
- **Multi-tenancy**: Control plane is shared across customers; data planes are isolated per customer.

## Consequences
- Agent registration requires outbound HTTPS from the VPC to the control plane — firewall rules must allow this.
- Agent version management requires a rolling upgrade process — documented in `scripts/agent-upgrade.sh`.
- The `syncflow-agent` module is fully self-contained — no control plane dependencies in its build.

## Links
- `syncflow-agent/` module, `AgentController.java`, `FleetManager.java`
- `docs/operations/DISASTER_RECOVERY.md`
