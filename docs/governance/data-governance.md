# SyncFlow Data Governance

> **Version:** 1.0  
> **Last Updated:** 2026-07-17  
> **Owner:** Data Engineering  

---

## Schema Version History

Every schema change detected by the SyncFlow metadata discovery is recorded in the `schema_versions` table.

```sql
CREATE TABLE schema_versions (
    id              VARCHAR(64) PRIMARY KEY,
    connection_id   VARCHAR(36) NOT NULL,
    schema          VARCHAR(255) NOT NULL,
    table_name      VARCHAR(255) NOT NULL,
    columns_json    JSONB NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    change_summary  TEXT,
    ddl_statement   TEXT,
    detected_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

**Query schema history for a table:**
```bash
curl -u admin:$TOKEN http://syncflow.example.com/api/governance/schema-history/{connectionId}?table=users
```

**Use case:** When a pipeline fails due to a schema mismatch, the schema version history shows exactly what changed and when. The pipeline can be rolled back to the previous schema version's mapping.

---

## Data Lineage

Every pipeline execution records its data lineage — which columns were read from which source, how they were transformed, and where they were written.

```sql
CREATE TABLE data_lineage (
    id                      VARCHAR(64) PRIMARY KEY,
    pipeline_id             VARCHAR(36) NOT NULL,
    source_connection_id    VARCHAR(36) NOT NULL,
    source_schema           VARCHAR(255),
    source_table            VARCHAR(255),
    source_columns          TEXT,
    dest_connection_id      VARCHAR(36) NOT NULL,
    dest_schema             VARCHAR(255),
    dest_table              VARCHAR(255),
    dest_columns            TEXT,
    transformation_summary  TEXT,
    rows_processed          BIGINT DEFAULT 0,
    timestamp               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

**Query lineage for a pipeline:**
```bash
curl -u admin:$TOKEN http://syncflow.example.com/api/governance/lineage?pipelineId={pipelineId}
```

**Use case:** Data lineage answers questions like "Where did this customer record come from?" and "What transformations were applied?" Useful for debugging, auditing, and compliance (GDPR Article 30).

---

## Data Classification

SyncFlow automatically classifies columns based on naming conventions and provides a data classification level per table.

### Column Tagging Rules

| Tag | Column Name Pattern | Classification |
|-----|-------------------|:--------------:|
| `PII_EMAIL` | Contains `email` | CONFIDENTIAL |
| `PII_PHONE` | Contains `phone`, `mobile` | CONFIDENTIAL |
| `PII_SSN` | Contains `ssn`, `social` | **RESTRICTED** |
| `PII_NAME` | Contains `first_name`, `last_name`, `full_name` | CONFIDENTIAL |
| `PII_ADDRESS` | Contains `address`, `street`, `zip` | CONFIDENTIAL |
| `PII_DOB` | Contains `dob`, `birth` | CONFIDENTIAL |
| `CREDENTIAL` | Contains `password`, `secret`, `token` | **RESTRICTED** |
| `FINANCIAL` | Contains `salary`, `account`, `credit` | CONFIDENTIAL |
| `HEALTH` | Contains `diagnosis`, `treatment` | CONFIDENTIAL |

### Table Classification Levels

| Classification | Description | Access Control |
|:--------------:|-------------|:--------------:|
| PUBLIC | No sensitive data | No restrictions |
| INTERNAL | Business data, not sensitive | Authenticated users |
| CONFIDENTIAL | PII or sensitive business data | Role-based access |
| **RESTRICTED** | Secrets, SSNs, credentials | Explicit authorization required |

**Example:**
```bash
# Classify a table's columns
curl -u admin:$TOKEN http://syncflow.example.com/api/governance/classify/{connectionId}?table=users
# Response: { "classification": "CONFIDENTIAL", "tags": { "email": "PII_EMAIL", "password_hash": "CREDENTIAL" } }
```

---

## PII Masking

PII and credential columns should be masked in the UI and log output.

| Context | Masking Strategy |
|---------|-----------------|
| API responses | `pipeline.mappings` — PII columns replaced with `***MASKED***` |
| Logs | All column values masked by `Credentials.toString()` (password: `******`) |
| AI Copilot context | `ContextCollector.sanitizeConnections()` strips credentials |
| Error messages | `GlobalExceptionHandler` never includes parameter values |
| Audit records | Column values not stored — only column names |

**Implementation:** PII masking is applied at the API DTO layer. The `ConnectionResponse` record omits `password` and `username` fields. The `PipelineDesignResponse` does not display column values — only column names and transformation rules.

---

## Retention Policies

| Entity | Retention | Action | Justification |
|--------|:---------:|:------:|:-------------:|
| Audit logs | 365 days | ARCHIVE | SOC 2 / compliance |
| Pipeline version history | 180 days | DELETE | Clean up stale designs |
| Dead letter events | 90 days | DELETE | Space management |
| PII data in destination | 365 days | ANONYMIZE | GDPR right to erasure |
| Data lineage records | 730 days | ARCHIVE | Long-term auditing |
| Schema versions | Permanent | — | Historical reference |

**Automated cleanup job:**
```sql
-- Delete DLQ events older than 90 days
DELETE FROM dead_letter_events WHERE timestamp < NOW() - INTERVAL '90 days';

-- Archive audit logs older than 365 days
INSERT INTO audit_logs_archive SELECT * FROM audit_logs WHERE timestamp < NOW() - INTERVAL '365 days';
DELETE FROM audit_logs WHERE timestamp < NOW() - INTERVAL '365 days';
```

---

## Audit Retention

Audit records are **immutable** (append-only). The `EnterpriseAuditStore` never allows UPDATE or DELETE operations on audit records (GDPR right-to-deletion is handled via anonymization, not deletion).

| Audit Category | Retention | Notes |
|----------------|:---------:|-------|
| User actions (login, logout, role changes) | 365 days | SOC 2 requirement |
| Pipeline CRUD operations | 365 days | Compliance |
| Connection CRUD operations | 365 days | Security monitoring |
| AI interactions | 90 days | Privacy consideration |
| API key usage | 730 days | Long-term security audit |

**Implementation:** `EnterpriseAuditRecord` is a record — no setters, no mutation. The `anonymize()` method replaces identifying information with `[ANONYMIZED]` but preserves the record structure.

---

## Data Ownership Metadata

Every pipeline and connection stores ownership metadata.

| Field | Example | Source |
|-------|---------|--------|
| `created_by` | `user@example.com` | JWT `sub` claim |
| `owned_by_team` | `data-platform` | Pipeline label |
| `data_steward` | `steward@example.com` | Connection metadata |
| `compliance_region` | `us-east-1` | Connection label |

**Set ownership on a pipeline:**
```bash
curl -X PUT -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id} \
  -d '{"labels": {"data_steward": "alice@example.com", "compliance_region": "eu-west-1"}}'
```

**Query all data stewardship assignments:**
```bash
curl -u admin:$TOKEN http://syncflow.example.com/api/dashboard/pipelines \
  | jq '.[] | {name, owner: .audit.createdBy, mappings: (.tableMappings | length)}'
```
