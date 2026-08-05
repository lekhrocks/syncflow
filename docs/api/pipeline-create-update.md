# Pipeline Create / Update API

> **Stability:** 🌐 Public (since 0.1.0) — see [API Stability Matrix](api-stability-matrix.md)
> **Base path:** `/api/pipelines`
> **Content-Type:** `application/json`

Reference for the two write endpoints on the Pipeline Designer API: `POST /api/pipelines` (create) and `PUT /api/pipelines/{id}` (update). Both operate on the same pipeline model; the difference is the partial-update semantics of `PUT`.

## POST /api/pipelines — Create

Creates a pipeline in `DRAFT` status. Returns the created pipeline with `201 Created`.

### Request body

| Field | Type | Required | Notes |
|-------|------|:--------:|-------|
| `name` | string | ✅ | Non-blank. |
| `sourceConnectionId` | string | ✅ | ID of an existing connection. |
| `sourceSchema` | string | ✅ | Schema on the source. |
| `sourceTable` | string | ✅ | Table/collection on the source. |
| `destConnectionId` | string | ✅ | ID of an existing connection. |
| `destSchema` | string | ✅ | Schema on the destination. |
| `destTable` | string | ✅ | Table/collection on the destination. |
| `destWriteMode` | string | optional | e.g. `UPSERT`. |
| `tableMappings` | array | optional | Column/primary-key/transformation mappings. Empty if omitted. |
| `syncMode` | enum | optional | One of `FULL_SNAPSHOT`, `CDC_INCREMENTAL`, `CDC_SNAPSHOT_AND_INCREMENTAL`. Defaults to `FULL_SNAPSHOT`. |
| `batchSize` | integer | optional | Defaults to `1000`. |
| `settings` | object | optional | Free-form key/value settings. |

### Example

```http
POST /api/pipelines
Content-Type: application/json

{
  "name": "pg-to-mongo",
  "sourceConnectionId": "conn-1",
  "sourceSchema": "public",
  "sourceTable": "users",
  "destConnectionId": "conn-2",
  "destSchema": "admin",
  "destTable": "users",
  "destWriteMode": "UPSERT",
  "tableMappings": [
    {
      "sourceTable": "users",
      "destinationTable": "users",
      "columnMappings": [
        { "sourceColumn": "email", "destinationColumn": "email" }
      ]
    }
  ],
  "syncMode": "FULL_SNAPSHOT",
  "batchSize": 500
}
```

```http
201 Created

{
  "id": "b6f4...",
  "name": "pg-to-mongo",
  "status": "DRAFT",
  "version": 1,
  "source": { "connectionId": "conn-1", "schema": "public", "tableOrCollection": "users" },
  "destination": { "connectionId": "conn-2", "schema": "admin", "tableOrCollection": "users", "writeMode": "UPSERT" },
  "tableMappings": [ /* same as request */ ],
  "settings": { "syncMode": "FULL_SNAPSHOT", "batchSize": 500 },
  "createdAt": "2026-08-05T09:00:00Z",
  "updatedAt": "2026-08-05T09:00:00Z"
}
```

### Error responses

| Status | Body `code` | When |
|--------|-------------|------|
| `400` | `VALIDATION_ERROR` | A required field is blank or invalid. |
| `400` | `INVALID_REQUEST_BODY` | Body is not valid JSON. |
| `404` | `NOT_FOUND` | Referenced connection does not exist (surfaced via pipeline validation). |

## PUT /api/pipelines/{id} — Update

Partially updates an existing pipeline. **Fields set to `null` (or omitted) keep their current value** — only the fields you send change. Returns `200 OK` with the updated pipeline and an incremented `version`.

### Request body

Same fields as Create, all **optional**. Every field is treated as "set if present":

| Field | Behavior when present |
|-------|----------------------|
| `name` | Replaces the name. |
| `sourceConnectionId` / `sourceSchema` / `sourceTable` | Replaces the whole source reference. Send all three together. |
| `destConnectionId` / `destSchema` / `destTable` | Replaces the whole destination reference. Send all three together. |
| `tableMappings` | Replaces the full mapping list. |
| `syncMode` | Replaces the sync mode (e.g. `FULL_SNAPSHOT`, `CDC_INCREMENTAL`). |
| `batchSize` | Replaces the batch size. |
| `settings` | Replaces the settings map. |

> **Settings merge:** `syncMode`, `batchSize`, and `settings` are applied independently — sending any one of them only changes that field; the others keep their current values. `settings` replaces the whole map (not a deep merge).

### Example — rename only

```http
PUT /api/pipelines/b6f4...
Content-Type: application/json

{ "name": "pg-to-mongo-v2" }
```

```http
200 OK

{
  "id": "b6f4...",
  "name": "pg-to-mongo-v2",
  "version": 2,
  "source": { /* unchanged */ },
  "destination": { /* unchanged */ },
  "tableMappings": [ /* unchanged */ ],
  "settings": { /* unchanged */ },
  "updatedAt": "2026-08-05T09:05:00Z"
}
```

### Error responses

| Status | Body `code` | When |
|--------|-------------|------|
| `404` | `NOT_FOUND` | Pipeline `{id}` does not exist. |
| `400` | `INVALID_REQUEST_BODY` | Body is not valid JSON. |

## Shared notes

- **Versioning:** every successful update bumps `version` and records a snapshot for `GET /api/pipelines/{id}/versions`. Create starts at `version: 1`.
- **Status:** create always yields `DRAFT`. There is no status field in create/update requests; transitions happen via validate / snapshot / capture / sync endpoints.
- **Stability:** these endpoints are 🌐 Public — changes require a 180-day deprecation notice and migration guide.
