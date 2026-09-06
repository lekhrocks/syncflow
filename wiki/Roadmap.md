# Roadmap

Feature status as of 2026-09-06. Tracked in `ARCHITECTURE_ANALYSIS.md`.

## Completed

### P0 — Security/Correctness

| ID | Feature | Status |
|----|---------|--------|
| F1 | Replace ThreadLocal tenant context with explicit passing | ✅ Done |
| F2 | Fix multi-table CDC processing in SyncOrchestrator | ✅ Done |
| F3 | Implement DELETE in DestinationRouter / writers | ✅ Done |
| F4 | Persist runtime state to DB (not in-memory maps) | ✅ Done |
| F5 | Add connection pooling to writers | ✅ Done |

### P1 — Scalability/Performance

| ID | Feature | Status |
|----|---------|--------|
| F6 | Batch writes in DestinationRouter | ✅ Done |
| F7 | Add circuit breaker + backpressure to event queues | ✅ Done |
| F8 | Make all hardcoded constants configurable | ✅ Done |
| F9 | Unify ValidationResult and ProcessingContext | ✅ Done |
| F10 | Extract ConnectionConfiguration mapper | ✅ Done |

### P2 — Architecture

| ID | Feature | Status |
|----|---------|--------|
| F11 | Create syncflow-runtime module | Deferred |
| F12 | Create syncflow-persistence module | ✅ Done |
| F13 | Implement distributed locking | ✅ Done |
| F14 | Add exactly-once CDC | ✅ Done |
| F15 | Parallel snapshot (PK-range chunking) | ✅ Done |

### P3 — Platform

| ID | Feature | Status |
|----|---------|--------|
| F16 | Migrate to reactive or structured concurrency | ✅ Done |

---

## In Progress

| ID | Feature | Notes |
|----|---------|-------|
| P4 | Jackson ObjectMapper reuse | ✅ Done (PR #73) |

---

## Upcoming

### P3 — Platform

| ID | Feature | Priority |
|----|---------|----------|
| F17 | Multi-region / geo-replication support | High |
| F18 | Implement connector plugin system (dynamic loading) | Medium |
| F19 | Add SQL-based transformation engine (push down to DB) | Medium |

### Deferred

| ID | Feature | Reason |
|----|---------|--------|
| F11 | Create syncflow-runtime module | Single consumer today; 490+ tests at risk |
| Reactive/WebFlux path | Equivalent to virtual threads; revisit if backpressure needed |
| ADR-006 | pgvector for semantic search | Wait until doc count > 1000 |

---

## SLOs

Defined in `docs/sre/slo.md`:

| Metric | Target |
|--------|--------|
| API availability | 99.95% |
| Agent heartbeat | 99.9% |
| REST P95 latency | < 250ms |
| CDC lag | < 10s |
| Sync lag | < 60s |
| RTO | < 5min |
| RPO | < 30s |
