# Database Schema

PostgreSQL 16+ with Flyway migrations (V1–V18).

## Migration History

| Version | Tables | Purpose |
|---------|--------|---------|
| V1 | `pipelines`, `pipeline_events` | Core pipeline entities |
| V2 | `connections` | Connection registry with encrypted credentials |
| V3 | `data_governance`, `data_lineage`, `column_tags` | Data governance |
| V4 | `pipeline_designs`, `pipeline_design_versions` | Pipeline designer with versioning |
| V5 | `cdc_offsets` | Debezium offset storage |
| V6 | `kafka_topics`, `kafka_messages` | Kafka sync persistence |
| V7 | `dead_letter_events` | DLQ with nullable event column |
| V8 | — | DLQ: add `replay_count` column |
| V9 | `app_users` | JWT authentication, default admin |
| V10 | — | Users: add `must_change_password` |
| V11 | — | Multi-tenancy: add `tenant_id` to domain tables |
| V12 | `snapshot_jobs`, `snapshot_checkpoints`, `sync_jobs`, `workflow_instances`, `quotas`, `audit_records`, `api_keys`, `agents`, `alert_events` | Runtime state persistence |
| V13 | `debezium_offsets` | Durable CDC offsets (BYTEA) |
| V14 | `active_captures` | Durable CDC capture state |
| V15 | — | Snapshot checkpoints: add `chunk_index` |
| V16 | — | Debezium offsets partitioning |
| V17 | — | Logical replication setup |
| V18 | `event_queue_snapshots` | CDC event queue snapshots |

## Key Tables

### connections
```sql
CREATE TABLE connections (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'system',
    name VARCHAR(255) NOT NULL,
    connection_type VARCHAR(50) NOT NULL,
    host VARCHAR(255),
    port INTEGER,
    database_name VARCHAR(255),
    options JSONB,
    encrypted_username TEXT,
    encrypted_password TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    db_version VARCHAR(100),
    driver_name VARCHAR(100),
    last_latency_ms BIGINT,
    last_checked TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### pipeline_designs
```sql
CREATE TABLE pipeline_designs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'system',
    name VARCHAR(255) NOT NULL,
    source_connection_id VARCHAR(36),
    destination_connection_id VARCHAR(36),
    payload JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'CREATED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### snapshot_jobs
```sql
CREATE TABLE snapshot_jobs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'system',
    pipeline_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### snapshot_checkpoints
```sql
CREATE TABLE snapshot_checkpoints (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    pipeline_id VARCHAR(36) NOT NULL,
    source_table VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    last_batch_number INTEGER NOT NULL,
    rows_processed BIGINT NOT NULL,
    cursor TEXT,
    created_at TIMESTAMP NOT NULL
);
```

### active_captures
```sql
CREATE TABLE active_captures (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    pipeline_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    offset JSONB,
    started_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(tenant_id, pipeline_id)
);
```

### dead_letter_events
```sql
CREATE TABLE dead_letter_events (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL DEFAULT 'system',
    pipeline_id VARCHAR(36) NOT NULL,
    event JSONB,
    error_message TEXT,
    error_code VARCHAR(50),
    retry_count INTEGER DEFAULT 0,
    replay_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    replayed_at TIMESTAMP
);
```

### app_users
```sql
CREATE TABLE app_users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roles TEXT[],
    must_change_password BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### debezium_offsets
```sql
CREATE TABLE debezium_offsets (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    pipeline_id VARCHAR(36) NOT NULL,
    key BYTEA NOT NULL,
    value BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

## Indexes

- `connections`: `(tenant_id)`, `(status)`
- `pipeline_designs`: `(tenant_id)`, `(status)`
- `snapshot_jobs`: `(tenant_id, pipeline_id)`, `(status)`
- `snapshot_checkpoints`: `(tenant_id, pipeline_id, source_table, chunk_index)`
- `active_captures`: `(tenant_id, pipeline_id)` UNIQUE
- `dead_letter_events`: `(tenant_id, pipeline_id)`, `(created_at)`
- `debezium_offsets`: `(tenant_id, pipeline_id)`
