# SyncFlow Compatibility Matrix

> **Last Updated:** 2026-07-17  
> **Maintainer:** Platform Engineering  
> **Review Cycle:** Monthly  

---

## Supported Databases

### Source & Destination

| Database | Minimum | Maximum | Tested | CDC | Snapshot | Notes |
|----------|:-------:|:-------:|:------:|:---:|:--------:|-------|
| PostgreSQL | 13 | 17 | 16 | ✅ | ✅ | Logical replication slot for CDC |
| MySQL | 8.0 | 8.4 | 8.0 | ✅ | ✅ | GTID + binlog row-based replication |
| MongoDB | 6.0 | 8.0 | 7.0 | ✅ | ✅ | Change Streams with resume tokens |
| Redis | 6.2 | 7.4 | 7.2 | ❌ | ❌ | Key-value metadata only |

### Driver Versions

| Database | Driver | Version | Bundle |
|----------|--------|:-------:|--------|
| PostgreSQL | `org.postgresql:postgresql` | 42.7.5 | `syncflow-connectors` |
| MySQL | `com.mysql:mysql-connector-j` | 9.2.0 | `syncflow-connectors` |
| MongoDB | `org.mongodb:mongodb-driver-sync` | 5.4.0 | `syncflow-connectors` |

---

## CDC Engine Versions

| Database | Engine | Version | Min SyncFlow |
|----------|--------|:-------:|:------------:|
| PostgreSQL | Debezium Embedded (pgoutput) | 3.1.0.Final | 0.1.0 |
| MySQL | Debezium Embedded (binlog) | 3.1.0.Final | 0.1.0 |
| MongoDB | Change Streams (`mongodb-driver-sync`) | 5.4.0 | 0.1.0 |

---

## Java Compatibility

| Java Version | Support | Build | Tested | Notes |
|:------------:|:-------:|:-----:|:------:|-------|
| 25 (current) | ✅ Full | ✅ | ✅ | LTS-equivalent; `--enable-preview` required |
| 24 | ⚠️ Partial | ❌ | ❌ | Not tested; API compatible at source level |
| 21+ | ❌ | ❌ | ❌ | Requires virtual thread support (JDK 21+) |

---

## Kubernetes Compatibility

| Kubernetes | Min SyncFlow | Ingress | Helm | KEDA | ArgoCD |
|:----------:|:------------:|:-------:|:----:|:----:|:------:|
| 1.32 | 0.1.0 | nginx 1.11+ | 3.12+ | 2.16+ | 2.14+ |
| 1.31 | 0.1.0 | nginx 1.11+ | 3.12+ | 2.16+ | 2.14+ |
| 1.30 | 0.1.0 | nginx 1.10+ | 3.10+ | 2.14+ | 2.12+ |
| 1.29 | 0.1.0 | nginx 1.9+ | 3.10+ | 2.14+ | 2.12+ |
| < 1.28 | ❌ | — | — | — | — |

### Resource Requirements (per replica)

| Environment | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-------------|:-----------:|:---------:|:--------------:|:------------:|
| Development | 250m | 1 | 256Mi | 512Mi |
| Staging | 500m | 2 | 512Mi | 1Gi |
| Production | 1 | 4 | 1Gi | 2Gi |

---

## Plugin SDK Compatibility

| SDK Version | Min Platform | Max Platform | Status |
|:-----------:|:------------:|:------------:|:------:|
| 0.1.0 | 0.1.0 | 0.2.0 | ✅ Current |
| 0.2.0 (future) | 0.2.0 | 1.0.0 | 🔄 Planned |

---

## API Compatibility

| API Version | SyncFlow Release | Status | Sunset |
|:-----------:|:----------------:|:------:|:------:|
| v1 | 0.1.0 | ✅ Active | — |
| v2 | 0.2.0 (future) | 🔄 Planned | — |

---

## UI / Browser Support

| Browser | Min Version | Tested | Notes |
|---------|:-----------:|:------:|-------|
| Google Chrome | 120 | ✅ | Primary development target |
| Mozilla Firefox | 115 | ✅ | Full feature parity |
| Apple Safari | 17 | ✅ | Minor CSS differences |
| Microsoft Edge | 120 | ✅ | Chromium-based — identical to Chrome |

---

## Observability Stack

| Component | Min Version | Integration | Config |
|-----------|:-----------:|:-----------:|--------|
| Prometheus | 2.45 | `/actuator/prometheus` | `prometheus/prometheus.yml` |
| Grafana | 10.2 | Data source + dashboards | `grafana/datasources.yaml` |
| Loki | 2.9 | JSON log shipping | Logback `JsonEncoder` |
| Tempo | 2.4 | OTLP gRPC | `management.otlp.tracing.endpoint` |
| OpenTelemetry Collector | 0.100 | OTLP receiver | `application.yml` |

---

## CI/CD Tooling

| Tool | Min Version | Purpose | Config |
|------|:-----------:|:--------:|--------|
| Gradle | 8.12 | Build system | `gradle/libs.versions.toml` |
| Docker | 24 | Container build | `docker/Dockerfile` |
| Helm | 3.12 | K8s packaging | `helm/syncflow/` |
| k6 | 0.50 | Load testing | `k6/benchmark.js` |
| Trivy | 0.73.0 | Vulnerability scan | `.github/workflows/ci-cd.yml` |
| Cosign | 2.2 | Container signing | CI `cosign-installer` |
| LitmusChaos | 3.0 | Chaos experiments | `scripts/chaos/experiment.yaml` |

---

## Upgrade Paths

| From | To | Method | Downtime | Migration Required |
|:----:|:--:|:------:|:--------:|:------------------:|
| 0.1.0 | 0.2.0 | Helm upgrade | 0 (rolling) | Flyway V3 |
| 0.2.0 | 1.0.0 | Helm upgrade | 0 (rolling) | Flyway V4 |

---

## Version Policy

| Component | Policy | Notes |
|-----------|--------|-------|
| Java | Latest + 1 | Current LTS and previous |
| Kubernetes | N-3 | Latest 4 minor versions |
| PostgreSQL | N-4 | Latest 5 major versions |
| MySQL | N-1 | Latest 2 major versions |
| MongoDB | N-2 | Latest 3 major versions |
| Redis | N-1 | Latest 2 major versions |
| Dependencies | Monthly Dependabot scans | Automated PR review |
