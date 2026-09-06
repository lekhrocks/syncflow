# Multi-Tenancy

SyncFlow supports multi-tenancy via tenant context threading and row-level isolation.

## Tenant Context

```java
public record TenantContext(
    TenantId tenantId,
    String organizationId,
    String workspaceId,
    String projectId,
    String userId,
    List<String> roles,
    Instant establishedAt
) {}
```

## How It Works

1. **JWT Authentication** — Login returns a JWT with tenant claims
2. **Context Extraction** — Filter extracts `TenantContext` from JWT
3. **Explicit Threading** — `TenantContext` passed through all orchestrators and workers
4. **Row-Level Scoping** — Every domain table has `tenant_id` column; queries filter by it

## Affected Tables

All domain tables include `tenant_id`:

- `connections`, `pipeline_designs`, `snapshot_jobs`, `sync_jobs`
- `dead_letter_events`, `processed_events`, `active_captures`
- `api_keys`, `audit_records`, `quotas`

## Default Tenant

The system tenant (UUID configured in migrations) is used for:
- Admin operations
- Agent registration
- System-level resources

## Organization Hierarchy

```
Organization
  └── Workspace
        └── Project
              └── Pipeline
```

Each level has its own RBAC scope. API keys can be scoped to specific organizations or projects.

## Audit Trail

Every state-changing operation is recorded in `audit_records`:

```java
AuditRecord
  ├── tenantId
  ├── userId
  ├── action (CREATE, UPDATE, DELETE, EXECUTE)
  ├── resourceType (PIPELINE, CONNECTION, etc.)
  ├── resourceId
  ├── timestamp
  └── details (JSONB)
```

GDPR compliance: right-to-delete anonymizes audit records instead of deleting them.
