# SyncFlow System Architecture

## Overview

SyncFlow is a globally distributed, enterprise-grade data synchronization platform. It connects heterogeneous databases through pluggable connectors and provides real-time data synchronization via CDC, snapshot, and streaming mechanisms.

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                   Enterprise UI (React 19)                   │
│  Dashboard │ Connections │ Pipelines │ Execution │ Admin    │
│  AI Copilot │ Plugin Marketplace │ Workflows │ Agents      │
├─────────────────────────────────────────────────────────────┤
│                  API Gateway / Ingress / Auth                 │
├─────────────────────────────────────────────────────────────┤
│                    Control Plane Services                     │
│  Pipeline │ Connection │ Metadata │ AI │ Workflow │ Agents  │
│  Auth │ RBAC │ Quota │ Audit │ Compliance │ Scheduler      │
├─────────────────────────────────────────────────────────────┤
│                    Data Plane Services                        │
│  Snapshot Engine │ CDC Engine │ Sync Engine │ Plugins       │
│  Connectors: PostgreSQL │ MySQL │ MongoDB │ Redis │ SDK     │
├─────────────────────────────────────────────────────────────┤
│                    Persistence Layer                          │
│  PostgreSQL (Metadata) │ Redis (Cache) │ S3 (Backups)       │
├─────────────────────────────────────────────────────────────┤
│                    Observability Stack                        │
│  Prometheus │ Grafana │ Loki │ Tempo │ OTel Collector       │
└─────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

- **Hexagonal Architecture**: Clean domain isolation per module; infrastructure depends on abstractions.
- **CQRS**: Read and write paths separated through distinct services.
- **Event-Driven CDC**: Debezium + MongoDB Change Streams capture changes as structured events.
- **Virtual Threads**: JDK 25 virtual threads for all concurrent execution (snapshot, CDC, sync workers).
- **Plugin SDK**: `syncflow-plugin-api` module provides independent versioned SPI for third-party connectors.
- **Control Plane / Data Plane**: Control plane manages state; agents execute tasks in customer environments.
- **Multi-Region HA**: Active-active replicas with topology spread constraints and KEDA autoscaling.
