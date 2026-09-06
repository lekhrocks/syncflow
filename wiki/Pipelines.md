# Pipelines

A pipeline defines how data flows from a source connection to a destination connection, including which tables to sync, column mappings, and transformation rules.

## Pipeline Design

A pipeline design is the declarative specification:

```
PipelineDesign
  ├── source (ConnectionReference → connectionId)
  ├── destination (ConnectionReference → connectionId)
  ├── tableMappings[]
  │     ├── sourceTable
  │     ├── destinationTable (optional, defaults to sourceTable)
  │     ├── primaryKey (destinationColumns)
  │     └── columnMappings[]
  │           ├── sourceColumn
  │           ├── destinationColumn
  │           └── transformation (optional SQL expression)
  └── settings
        ├── batchSize (default 100)
        └── syncMode (SNAPSHOT, CDC, BOTH)
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines` | Create pipeline design |
| `GET` | `/api/pipelines` | List all pipelines |
| `GET` | `/api/pipelines/{id}` | Get pipeline by ID |
| `PUT` | `/api/pipelines/{id}` | Update pipeline |
| `DELETE` | `/api/pipelines/{id}` | Delete pipeline |
| `POST` | `/api/pipelines/{id}/validate` | Validate pipeline |
| `POST` | `/api/pipelines/{id}/rollback` | Rollback to version |
| `GET` | `/api/pipelines/{id}/versions` | List pipeline versions |
| `GET` | `/api/pipelines/{id}/preview` | Preview pipeline output |
| `GET` | `/api/pipelines/{id}/conflicts` | Detect mapping conflicts |

## Versioning

Every pipeline update creates a version in `pipeline_design_versions`. Rollback restores a previous version without data loss.

## Validation

`POST /api/pipelines/{id}/validate` checks:

- Source and destination connections exist and are healthy
- Source tables exist in the source schema
- Column types are compatible
- Primary key columns exist
- No circular transformations

## Conflict Detection

`GET /api/pipelines/{id}/conflicts` detects:

- Multiple source columns mapped to the same destination column
- Primary key columns not included in the mapping
- Transformation expressions referencing non-existent columns
