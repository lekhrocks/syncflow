# SyncFlow Failure Mode Matrix

> **Version:** 1.0  
> **Last Updated:** 2026-07-14  
> **Owner:** Platform Engineering  

---

## How to Use This Matrix

Each row describes a failure mode, the expected system behavior, detection mechanism, recovery time, and which tests validate the behavior. Use this during incident response, chaos engineering experiments, and pre-release verification.

---

## 🔴 Tier 1 — Data Loss Scenarios

### F1: Control Plane Database Unavailable

| Attribute | Value |
|-----------|-------|
| **Failure** | PostgreSQL (metadata store) is unreachable or rejecting connections. |
| **Detection** | Connection pool exceptions, HikariCP health check failures. Actuator `/health` returns DEGRADED. Alert: `syncflow_pg_down`. |
| **Expected Behavior** | All read endpoints returning data from in-memory caches continue working (connections, pipelines, metadata with Caffeine). Write operations (create/update/delete) fail with 503. CDC and snapshot engines in agents continue running — they don't depend on the control plane database. |
| **Recovery** | Automatic when database recovers — HikariCP retries connections. No data loss because writes are rejected (not accepted silently). |
| **RTO** | 0s for reads (cached) / immediate on DB recovery for writes. |
| **Test Coverage** | `HealthAggregator` returns status:DOWN. Integration tests validate Degraded health status. |

### F2: CDC Connector Failure

| Attribute | Value |
|-----------|-------|
| **Failure** | Debezium engine crashes or loses connection to source database. |
| **Detection** | `CdcCapableConnector.isCdcActive()` returns false. Alert: `syncflow_cdc_stopped`. CaptureLifecycle detects INACTIVE status. |
| **Expected Behavior** | No events published while connector is down. Offset is preserved in `OffsetStore`. On restart, `DebeziumCdcConnector` starts from last committed offset — no events are lost. The sync orchestrator continues running with empty queue (idles). |
| **Recovery** | Automatic: `CaptureLifecycle.start()` resumes CDC from stored offset. If Debezium engine exits, it is restarted via virtual thread. |
| **RTO** | < 30s (CDC recovery time SLO). |
| **Test Coverage** | `CdcIntegrationTest.offsetPreservedAfterStop`, `.cdcConnectorLifecycleStartStop`. `CdcEventParserUnitTest.offsetTrackerBasic`. |

### F3: Destination Database Unavailable

| Attribute | Value |
|-----------|-------|
| **Failure** | Destination PostgreSQL/MySQL/MongoDB/Redis rejects writes or is unreachable. |
| **Detection** | `DestinationRouter.write()` returns `WriteResult(false, error)`. JDBC `SQLException` on executeBatch. |
| **Expected Behavior** | `RetryEngine` evaluates as transient error → exponential backoff (1s, 2s, 4s). After 3 retries, event is sent to Dead Letter Queue. Sync orchestrator continues processing subsequent events (they also fail and are queued to DLQ). No events are dropped — all are either delivered or DLQ'd. |
| **Recovery** | Manual: Fix destination, replay from DLQ via `POST /api/dlq/{id}/replay`. Or automated: destination becomes available → events succeed → consumed from DLQ. |
| **RTO** | N/A (requires manual intervention for DLQ replay). |
| **Test Coverage** | `SyncEngineUnitTest.retryExhaustedMovesToDlq`, `.retryExponentialBackoff`, `SyncIntegrationTest.replayRemoves`. |

### F4: Source Database Unavailable

| Attribute | Value |
|-----------|-------|
| **Failure** | Source database is unreachable during snapshot or CDC. |
| **Detection** | JDBC connection timeout on `readBatch()`. Debezium reports connection error. |
| **Expected Behavior** | **Snapshot:** `SnapshotExecutor` catches exception, sets job status to FAILED, saves checkpoint at last completed batch. **CDC:** Debezium engine enters reconnection loop; no data loss because WAL/binlog position is preserved on the source. |
| **Recovery** | **Snapshot:** Restart from saved checkpoint — resumes at `lastBatchNumber + 1`. **CDC:** Automatic when source database recovers — Debezium reconnects and resumes from last offset. |
| **RTO** | **Snapshot:** Immediate from checkpoint. **CDC:** < 30s after source recovery. |
| **Test Coverage** | `CheckpointManagerUnitTest.resumeFromLastCheckpoint`, `CdcIntegrationTest.cdcConnectorLifecycleStartStop`. |

---

## 🟠 Tier 2 — Degraded Performance

### F5: Worker Pod Crash

| Attribute | Value |
|-----------|-------|
| **Failure** | Control Plane pod terminates unexpectedly (OOM, node failure, manual kill). |
| **Detection** | Kubernetes liveness probe fails → pod restart. `KubernetesIntegrationTest.livenessProbe()` validates. |
| **Expected Behavior** | Load balancer routes traffic to remaining pod(s). In-memory state (workflow instances, checkpoint store) is lost — only the new pod's state exists. New workflows can be created immediately after restart. Leader election detects loss and promotes a standby pod. |
| **Recovery** | Automatic via Kubernetes ReplicaSet. PDB ensures `minAvailable=1`. |
| **RTO** | < 30s (pod restart + startup probe + readiness). |
| **Test Coverage** | `KubernetesIntegrationTest.workflowStateAfterRestart`, `.newWorkflowAfterRestart`, `.healthDuringShutdown`. |

### F6: Leader Pod Crash

| Attribute | Value |
|-----------|-------|
| **Failure** | The pod acting as workflow scheduler leader crashes. |
| **Detection** | Heartbeat timeout (>30s). `WorkflowScheduler.isLeaderAlive()` returns false. |
| **Expected Behavior** | Remaining pod detects leader loss, calls `becomeLeader()`, starts its tick loop. No workflow progress during the election window (<30s). In-memory task queue is lost — tasks need to be re-enqueued on the new leader. |
| **Recovery** | Automatic — leader election is built into the `WorkflowScheduler`. |
| **RTO** | < 30s. |
| **Test Coverage** | `KubernetesIntegrationTest.LeaderElection` (5 tests: default, become, toggle, heartbeat, death). |

### F7: Agent Disconnect

| Attribute | Value |
|-----------|-------|
| **Failure** | Managed agent stops sending heartbeats (network partition, process crash, node failure). |
| **Detection** | `FleetManager.pruneOffline()` runs on each heartbeat — marks agent as UNREACHABLE after 60s without heartbeat. Alert: `syncflow_agents_online < 1`. |
| **Expected Behavior** | Control plane marks agent UNREACHABLE. No new work is assigned to the agent. Work assigned to the disconnected agent is not reassigned automatically (future: work reassignment). Agent maintains its local checkpoint state. |
| **Recovery** | Agent restarts and re-registers via `POST /api/agents/register`. Control plane creates new agent entry. For snapshot/CDC work: agent resumes from local checkpoint on reconnect. |
| **RTO** | N/A (work remains pending until agent reconnects or is manually reassigned). |
| **Test Coverage** | `ControlPlaneUnitTest.markAgentOffline`, `.disconnectUnknownAgentIsNoOp`, `.capabilityMatcherFindsMatchingAgent`. |

### F8: Network Partition

| Attribute | Value |
|-----------|-------|
| **Failure** | Network connectivity between control plane and agent, or between control plane and database, is lost. |
| **Detection** | Connection timeouts on HTTP calls. Heartbeat stops arriving. |
| **Expected Behavior** | **Agent ↔ CP:** Agent continues local execution (snapshot, CDC, sync) — all work is driven by local state and checkpointed locally. When connectivity returns, agent re-registers and reports latest checkpoints. **CP ↔ DB:** Read endpoints served from cache. Write operations fail. |
| **Recovery** | Automatic when network connectivity is restored. No data loss due to local checkpointing on agents and idempotency on the CP side. |
| **RTO** | Time to network recovery. |
| **Test Coverage** | `ControlPlaneUnitTest.heartbeatForUnknownAgentReturnsEmpty`. |

---

## 🟡 Tier 3 — Operational Degradation

### F9: Destination Full / Quota Exceeded

| Attribute | Value |
|-----------|-------|
| **Failure** | Destination database runs out of disk space or reaches connection/user quota. |
| **Detection** | JDBC `SQLException` with disk full / quota exceeded message. |
| **Expected Behavior** | Treated as permanent error (disk full requires manual intervention). Event goes directly to DLQ without retry. Alert: `syncflow_dlq_growth`. |
| **Recovery** | Free destination space → replay from DLQ. |
| **RTO** | Manual. |
| **Test Coverage** | `SyncEngineUnitTest.permanentErrorGoesDirectlyToDlq`. |

### F10: Schema Change (DDL)

| Attribute | Value |
|-----------|-------|
| **Failure** | Column added/dropped/renamed in source database. |
| **Detection** | Debezium schema change event. Metadata discovery returns different columns than pipeline mappings. |
| **Expected Behavior** | CDC continues capturing events using the DDL-compensating algorithm (Debezium's schema history topic). Sync transformer may fail if mapped column no longer exists — event goes to DLQ. Pipeline validation shows schema mismatch. |
| **Recovery** | Refresh metadata via `POST /connections/{id}/metadata/refresh`. Update pipeline mappings to match new schema. Replay affected events from DLQ. |
| **RTO** | Manual. |
| **Test Coverage** | `MetadataIntegrationTest.discoverColumns`, `.refreshMetadata`. |

### F11: Plugin Crash

| Attribute | Value |
|-----------|-------|
| **Failure** | Plugin connector throws exception during `discoverSchemas()`, `readBatch()`, or `startCDC()`. |
| **Detection** | Exception propagated through `PluginManager`. Plugin marked as ERROR in lifecycle. |
| **Expected Behavior** | The plugin's connector entry is retained in ERROR state. Other plugins and built-in connectors are unaffected. The exception is logged and audit-recorded. |
| **Recovery** | Disable plugin, fix plugin JAR, reinstall. |
| **RTO** | Manual. |
| **Test Coverage** | `PluginEngineUnitTest` (22 tests, lifecycle state coverage). |

---

## ⚫ Cascading Failure Prevention

| Scenario | Prevention |
|----------|------------|
| **Retry Storm** | Exponential backoff (1s, 2s, 4s) caps at 3 retries before DLQ. Permanent errors skip retry entirely. Alert if retry rate > 5/s. |
| **Memory Exhaustion** | Bounded `LinkedBlockingQueue` (10,000 events). Caffeine cache with max size limits. JVM heap monitoring with alert at 90%. |
| **Connection Pool Exhaustion** | HikariCP max 10 connections per service. Virtual threads don't hold connections during I/O wait. Timeouts at 30s. |
| **DLQ Overflow** | DLQ is in-memory with no capacity bound (soft limit via alert). Future: DLQ spill to disk or database. |
| **Agent Flood** | Agent registration does not have server-side rate limiting. Mitigation: agents authenticate via mTLS — unauthenticated agents cannot register. |

---

## Recovery Time Summary

| Failure | Detection | RTO | Automatic? |
|---------|:---------:|:---:|:----------:|
| F1: DB unavailable | Instant | 0s (reads) / DB recovery (writes) | ✅ |
| F2: CDC connector fail | < 1s | < 30s | ✅ |
| F3: Destination unavailable | < 1s | Manual (DLQ replay) | ❌ |
| F4: Source unavailable | < 5s | < 30s (CDC) / manual (snapshot) | ✅ |
| F5: Worker crash | < 10s | < 30s | ✅ |
| F6: Leader crash | < 30s | < 30s | ✅ |
| F7: Agent disconnect | < 60s | Manual (agent restart) | ❌ |
| F8: Network partition | < 15s | Network recovery | ✅ |
| F9: Destination full | < 1s | Manual (disk cleanup) | ❌ |
| F10: Schema change | Immediate | Manual (remap) | ❌ |
| F11: Plugin crash | Immediate | Manual (reinstall) | ❌ |

---

## Test Coverage by Failure Mode

| Failure | Unit Tests | Integration Tests | Chaos Tests |
|---------|:----------:|:-----------------:|:-----------:|
| F1: DB unavailable | ✅ Health check | ✅ Degraded status | ⏳ Planned |
| F2: CDC connector fail | ✅ 19 parser tests | ✅ 11 CDC tests | ⏳ Planned |
| F3: Destination unavailable | ✅ Retry/DLQ: 10 tests | ✅ DLQ: 7 tests | ⏳ Planned |
| F4: Source unavailable | ✅ Checkpoint: 13 tests | ✅ Resume: 3 tests | ⏳ Planned |
| F5: Worker crash | ✅ K8s: 4 tests | — | ✅ LitmusChaos |
| F6: Leader crash | ✅ Leader: 5 tests | — | ✅ LitmusChaos |
| F7: Agent disconnect | ✅ Agent: 7 tests | ✅ Agent: 7 tests | ⏳ Planned |
| F8: Network partition | ✅ Heartbeat: 3 tests | — | ✅ LitmusChaos |
| F9: Destination full | ✅ Permanent error: 1 test | — | ⏳ Planned |
| F10: Schema change | ✅ Metadata: 29 tests | ✅ Metadata: 10 tests | ⏳ Planned |
| F11: Plugin crash | ✅ Plugin: 22 tests | — | ⏳ Planned |
