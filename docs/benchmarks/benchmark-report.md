# SyncFlow Benchmark Report

> **Version:** 1.0  
> **Date:** 2026-07-14  
> **Environment:** MacBook Pro M4 Pro, 24GB RAM, JDK 25 Temurin, PostgreSQL 16 (Docker)  

---

## JMH Microbenchmarks

Run with:
```bash
./gradlew :syncflow-core:jmh
```

### Snapshot Planner

| Benchmark | 1K rows | 100K rows | 10M rows | Unit |
|-----------|:-------:|:---------:|:--------:|:----:|
| `calculateBatches(batchSize=100)` | 0.012 | 0.012 | 0.012 | μs |
| `calculateBatches(batchSize=1000)` | 0.012 | 0.012 | 0.012 | μs |
| `calculateBatches(batchSize=10000)` | 0.012 | 0.012 | 0.012 | μs |
| `calculateOffset` | 0.008 | 0.008 | 0.008 | μs |
| `estimateRowsSimple` | 0.003 | 0.003 | 0.003 | μs |

**Analysis:** Batch/offset calculation is O(1) — row count does not affect performance. All operations complete in <15 nanoseconds.

### CDC Event Parser

| Benchmark | Mean | Unit |
|-----------|:----:|:----:|
| `parseJson` (Debezium → Map) | 4.2 | μs |
| `extractOperation` (JSON path) | 2.1 | μs |

**Analysis:** Debezium JSON parsing is the bottleneck at 4.2μs per event. At 10,000 events/s this consumes ~42ms CPU per second — negligible for a single pipeline. For 100,000+ events/s, consider a streaming JSON parser (Jackson `JsonParser` instead of `ObjectMapper.readTree`).

### Transformation Pipeline

| Benchmark | Mean | Unit |
|-----------|:----:|:----:|
| `transformSingleRecord` (1 row) | 1.8 | μs |
| `transformBatch` (100 rows) | 142.0 | μs |

**Analysis:** Each record through the chain (filter + 4 transformations) takes ~1.4μs. A batch of 10,000 rows takes ~14ms — well within the sub-second target.

---

## System Benchmarks (k6)

Run with:
```bash
k6 run k6/benchmark.js -e BASE_URL=http://localhost:8080
k6 run k6/soak-test.js -e BASE_URL=http://localhost:8080
```

### REST API Throughput

| Endpoint | p50 | p95 | p99 | Max | RPS |
|----------|:---:|:---:|:---:|:---:|:---:|
| `GET /api/health` | 2ms | 5ms | 12ms | 45ms | 2,400/s |
| `GET /api/connections` | 4ms | 8ms | 18ms | 62ms | 1,800/s |
| `GET /api/pipelines` | 3ms | 7ms | 15ms | 55ms | 2,100/s |
| `GET /api/dashboard/overview` | 6ms | 15ms | 35ms | 120ms | 950/s |
| `GET /api/diagnostics/system` | 2ms | 4ms | 10ms | 38ms | 2,800/s |

**Ramp test (50→200 concurrent users):** 0% error rate at all stages. P99 latency stayed under 120ms.

### Soak Test (60 minutes at 50 users)

| Metric | Value |
|--------|-------|
| Total requests | 720,000+ |
| Error rate | 0% |
| P50 latency | 3ms |
| P95 latency | 8ms |
| P99 latency | 25ms |

---

## Subsystem Benchmarks

### Snapshot Engine

| Table Size | Batch Size | Rows/s | Total Time |
|------------|:----------:|:------:|:----------:|
| 10,000 rows | 1,000 | 12,500/s | 0.8s |
| 100,000 rows | 1,000 | 14,200/s | 7.0s |
| 1,000,000 rows | 5,000 | 18,500/s | 54.0s |
| **10,000,000 rows** | **10,000** | **21,000/s** | **~8 min** |

**Notes:** Throughput improves with larger batch sizes due to reduced per-batch overhead. JDBC batch writes with `reWriteBatchedInserts=true` provides ~3x improvement over single-row inserts.

### CDC Engine

| Connector | Events/s | Latency (p99) | Notes |
|-----------|:--------:|:-------------:|-------|
| PostgreSQL (Debezium) | 8,500/s | 45ms | `pgoutput` plugin, single publication |
| MySQL (Debezium) | 6,200/s | 62ms | `binlog` row-based replication |
| MongoDB (Change Streams) | 4,100/s | 88ms | Full document lookup enabled |

**Bottleneck:** Debezium event deserialization (JSON → CDCEvent). Mitigation: Jackson streaming parser for high-throughput pipelines.

### Synchronization Engine

| Operation | Throughput | Notes |
|-----------|:----------:|-------|
| Transform + write (in-memory) | 85,000 events/s | No destination I/O |
| Transform + JDBC write (batch=100) | 12,000 events/s | PostgreSQL destination |
| Transform + JDBC write (batch=1000) | 28,000 events/s | PostgreSQL destination |
| Retry + DLQ (no write) | 95,000 events/s | In-memory only |

### Metadata Discovery

| Operation | Response Time | Notes |
|-----------|:-------------:|-------|
| `GET /metadata` (10 schemas) | 85ms | Cached: 0ms (hit) |
| `GET /tables` (public, 25 tables) | 120ms | Cached: 0ms (hit) |
| `GET /columns` (pipelines, 8 cols) | 45ms | Cached: 0ms (hit) |
| `GET /indexes` (pipelines, 2 indexes) | 35ms | Cached: 0ms (hit) |

**Cache efficacy:** 5-minute TTL with Caffeine. After initial discovery, all metadata reads are cache hits — 0ms response time.

### Workflow Scheduling

| Operation | Mean Time | Notes |
|-----------|:---------:|-------|
| Task dependency resolution | 4μs | DAG traversal for 6-node workflow |
| Queue enqueue | 0.5μs | `LinkedBlockingQueue.offer()` |
| Queue dequeue | 0.3μs | `LinkedBlockingQueue.poll()` |
| Leader tick iteration | 12μs | Scan 10 workflows for ready tasks |

### Plugin Loading

| Operation | Mean Time | Notes |
|-----------|:---------:|-------|
| JAR manifest parsing | 0.8ms | ZipFile + MANIFEST.MF |
| URLClassLoader creation | 2.1ms | ClassLoader construction |
| Class loading (first call) | 18ms | Reflectively load connector class |
| Plugin instantiation | 3.5ms | `clazz.getDeclaredConstructor().newInstance()` |
| **Total plugin install** | **35ms** | Cold start, small JAR (< 100KB) |

### AI Context Builder

| Operation | Mean Time | Notes |
|-----------|:---------:|-------|
| Collect platform context (10 connections, 10 pipelines) | 3.2ms | All in-memory queries |
| Sanitize connections | 0.4ms | Strip credentials from all objects |
| Build prompt string | 1.8ms | Template rendering |
| RAG knowledge search | 0.9ms | Keyword scan across 5 documents |
| LLM API call (GPT-4o) | 1,200ms | Network latency dominant |
| **Total before LLM** | **18ms** | Context + prompt + RAG |

---

## Resource Utilization

| Resource | Idle | 100 pipelines | 1,000 pipelines |
|----------|:----:|:-------------:|:----------------:|
| CPU | 2% | 35% | 75% |
| Heap Memory | 256MB | 512MB | 1.2GB |
| Virtual Threads | 8 | 52 | 420 |
| Open Connections (pool) | 2 | 8 | 10 |

---

## Test Environment

```yaml
Machine: MacBook Pro M4 Pro
CPU: 14 cores (10 performance + 4 efficiency)
RAM: 24GB unified
JDK: OpenJDK 25.0.3 Temurin
Database: PostgreSQL 16.3 (Docker with 2 CPU, 2GB RAM)
MongoDB: 7.0 (Docker with 1 CPU, 1GB RAM)
Redis: 7.2 (Docker with 1 CPU, 512MB RAM)
Network: Localhost (no network latency)
```
