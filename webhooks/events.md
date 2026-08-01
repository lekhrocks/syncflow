# SyncFlow Webhook Events

## Event Types

| Event | Trigger | Payload |
|-------|---------|---------|
| `pipeline.created` | Pipeline created | `{id, name, status}` |
| `pipeline.deleted` | Pipeline deleted | `{id, name}` |
| `pipeline.failed` | Pipeline execution failed | `{id, name, error, timestamp}` |
| `snapshot.completed` | Snapshot finished | `{pipelineId, rowsProcessed, durationMs}` |
| `snapshot.failed` | Snapshot failed | `{pipelineId, error, batchNumber}` |
| `cdc.stopped` | CDC capture stopped | `{pipelineId, reason, lastOffset}` |
| `sync.failed` | Sync processing failed | `{pipelineId, eventId, error}` |
| `connection.lost` | Database connection lost | `{connectionId, type, host}` |
| `schema.changed` | Schema change detected | `{connectionId, table, change}` |
| `workflow.completed` | Workflow completed | `{workflowId, pipelineId, duration}` |
| `agent.offline` | Agent heartbeat lost | `{agentId, hostname, lastHeartbeat}` |

## Delivery

Webhooks are dispatched via `WebhookDispatcher` in-process. Future delivery methods:

| Method | Path | Status |
|--------|------|--------|
| In-memory listeners | `WebhookDispatcher.subscribe()` | ✅ Available |
| HTTP POST | `POST /api/webhooks/{id}` | 🔄 Planned |
| Kafka topic | `syncflow.events` | 🔄 Planned |
| SSE stream | `GET /api/events/stream` | 🔄 Planned |
