# Runbook: High DLQ Count

> **Severity:** SEV2 — Permanent errors accumulating, data may be lost
> **Owner:** Platform Engineering

## Symptoms
- `DLQGrowing` alert firing (DLQ count > 100 for 10m)
- `GET /api/dashboard/metrics` shows large DLQ size
- Events failing repeatedly without recovery
- Destination missing records (customer visible)

## Possible Causes
1. Destination schema mismatch (column renamed/deleted)
2. Data type conversion failure (source-destination incompatibility)
3. Primary key conflict (duplicate rows)
4. Foreign key violation (missing referenced row in destination)
5. Destination constraint violation (NOT NULL, CHECK)
6. Transformation script error (expression evaluation failure)

## Diagnosis

```bash
# Check total DLQ size
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq 'length'

# Inspect specific DLQ entries
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq '.[0:3] | .[] | {reason: .reason.message, retryCount: .retryCount, operation: .originalEvent.operation}'

# Group by error code
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq 'group_by(.reason.code) | .[] | {code: .[0].reason.code, count: length}'

# Check distribution by pipeline
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq 'group_by(.pipelineId) | .[] | {pipeline: .[0].pipelineId, count: length}'

# Inspect the original event that failed
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq/{eventId} | jq '.originalEvent'
```

## Recovery Steps

### Step 1: Triage by error type
```bash
# List DLQ entries grouped by error code
ERRORS=$(curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq -r '.[].reason.code' | sort | uniq -c | sort -rn)
echo "$ERRORS"
```

### Step 2: Fix common issues

## For schema mismatch
```bash
# Refresh metadata
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/connections/{connId}/metadata/refresh

# Update pipeline mappings
curl -u admin:$TOKEN http://syncflow.example.com/api/pipelines/{id} | jq '.tableMappings[].columnMappings[] | {source, dest}'
# Update via PUT /api/pipelines/{id}
```

## For data conversion errors
```bash
# Check source vs destination column types
curl -u admin:$TOKEN http://syncflow.example.com/api/connections/{sourceId}/metadata/data | jq '.columns[] | {name, type}'
curl -u admin:$TOKEN http://syncflow.example.com/api/connections/{destId}/metadata/data | jq '.columns[] | {name, type}'

# Add transformation to convert types
# Example: integer → string conversion
```

## For PK conflicts
```bash
# Delete conflicting row in destination
psql -h {dest_host} -c "DELETE FROM {table} WHERE id = {pk_value};"

# Or merge
psql -h {dest_host} -c "
  INSERT INTO {table} (id, name, ...) VALUES ({pk}, ...)
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
"
```

### Step 3: Replay DLQ
```bash
# Replay specific event
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/dlq/{id}/replay

# Or batch replay all events for a pipeline
for id in $(curl -u admin:$TOKEN http://syncflow.example.com/api/dlq?pipelineId={pid} | jq -r '.[].id'); do
  curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/dlq/$id/replay
  echo "Replayed: $id"
done
```

### Step 4: Verify events processed
```bash
# Check sync statistics after replay
curl -u admin:$TOKEN http://syncflow.example.com/api/sync/jobs/{id} | jq '.statistics'

# Check DLQ size decreased
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq 'length'
```

## Escalation
- DLQ > 1000 events: page Platform Lead
- DLQ growing faster than replay rate: SEV2, page platform team
- Data loss confirmed: SEV1, page Platform Lead + Data Engineering

## Post-Incident
- [ ] Add DLQ growth alert with per-pipeline breakdown
- [ ] Add DLQ trends dashboard (rate of growth, error code distribution)
- [ ] Add automated DLQ replay for known error patterns
- [ ] Add schema validation before pipeline start
- [ ] Review transform error handling for unhandled data types
