# API Migration Guide: v1 → v2

> **Status:** Draft  
> **v1 Sunset Date:** 2026-10-01  
> **v2 Release Date:** 2026-07-01  

## Why v2?

v2 introduces:
- Consistent pagination across all list endpoints
- Standardized error response format (RFC 7807 Problem Details)
- Deprecation headers for gradual migration
- New fields without breaking existing clients

## Breaking Changes

### 1. Pagination (ALL list endpoints)

**v1 (default):** Unbounded lists — returns all results.

```http
GET /api/connections
→ 200 [{...}, {...}, ...]  (unbounded array)
```

**v2 (Accept-Version: v2):** Cursor-based pagination.

```http
GET /api/connections?page[size]=50&page[cursor]=abc
→ 200 {
  "data": [{...}, {...}],
  "meta": { "total": 152, "next_cursor": "def" }
}
```

**Migration:** Add pagination parameters to list calls. Remove any client code that assumes unbounded arrays.

### 2. Error Response Format

**v1:**
```json
{
  "code": "NOT_FOUND",
  "message": "Connection not found: abc",
  "correlationId": "abc123",
  "status": 404,
  "timestamp": "..."
}
```

**v2 (RFC 7807):**
```json
{
  "type": "https://docs.syncflow.io/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Connection not found: abc",
  "instance": "/api/connections/abc",
  "correlationId": "abc123"
}
```

**Migration:** Update error parsers to handle both formats. The v1 format continues to work until the sunset date.

### 3. Field Naming Consistency

**v1:** Mixed snake_case and camelCase in responses.

**v2:** All response fields use camelCase consistently.

| Field | v1 | v2 |
|-------|----|----|
| Connection type | `connection_type` | `connectionType` |
| Created at | `created_at` | `createdAt` |
| Table mapping | `table_mappings` | `tableMappings` |

**Migration:** Update client-side deserialization to accept both `snake_case` and `camelCase` (Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` handles this).

## New Features in v2

| Feature | Description | Endpoint |
|---------|-------------|----------|
| Pagination | Cursor-based for all list endpoints | All `GET /api/*` |
| Bulk operations | Batch create/update connections | `POST /api/connections/bulk` |
| Webhook events | Subscribe to pipeline state changes | `POST /api/webhooks` |
| Rate limit headers | `X-RateLimit-Remaining`, `X-RateLimit-Reset` | All endpoints |
| Request tracing | `X-Request-Id` in responses | All endpoints |

## Backward Compatibility Guarantee

**v1 will not be removed before 2026-10-01.**

During the migration period:
- `Accept-Version` header defaults to `v1` when absent
- v1 and v2 share all data — no migration needed
- Response format is the only difference
- All v1 endpoints continue working unmodified
- v2 deprecation warnings are sent as HTTP headers, not errors

## Testing Compatibility

```bash
# v1 (default)
curl -s https://syncflow.example.com/api/connections | jq 'type'
# → "array" (v1 format)

# v2
curl -s -H "Accept-Version: v2" https://syncflow.example.com/api/connections | jq 'type'
# → "object" with "data" key (v2 format)

# v2 with pagination
curl -s -H "Accept-Version: v2" \
  "https://syncflow.example.com/api/connections?page[size]=10" | jq '.meta'
```

## Rollback Plan

If a client is broken by a v2 change:
1. Remove `Accept-Version: v2` header from client requests — falls back to v1
2. File a compatibility bug
3. Fix is deployed without requiring client changes
