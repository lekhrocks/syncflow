# CDC Capture

CDC (Change Data Capture) captures real-time changes from source databases using Debezium, then routes them to destination writers.

## Architecture

```
Source DB (WAL/binlog)
    |
    v
Debezium Engine (runs on virtual thread)
    |
    v
CaptureLifecycle (start/stop/pause/resume)
    |
    +--> In-memory event queue (per pipeline)
    |         |
    |         v
    |    SyncOrchestrator (drain + transform + write)
    |         |
    |         v
    |    DestinationRouter (batched writes)
    |
    +--> [Optional] Kafka transport (when syncflow.kafka.enabled=true)
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines/{id}/capture/start` | Start CDC capture |
| `POST` | `/api/pipelines/{id}/capture/stop` | Stop CDC capture |
| `POST` | `/api/pipelines/{id}/capture/pause` | Pause CDC capture |
| `POST` | `/api/pipelines/{id}/capture/resume` | Resume CDC capture |
| `GET` | `/api/pipelines/{id}/capture/status` | Get capture status + event count |

## Offset Management

CDC offsets track the position in the source's WAL/binlog. Stored in `debezium_offsets` (Postgres BYTEA for durable key/value pairs).

On restart, Debezium resumes from the last committed offset — no data loss, no duplication.

## Event Processing

`SyncOrchestrator` processes CDC events:

1. **Drain** — Pulls up to `batch-size` events from the queue
2. **Group** — Groups by table and operation type
3. **Transform** — Applies column mappings and transformations
4. **Write** — Calls `DestinationRouter.writeBatch()` (single flush + commit per table)
5. **Idempotency** — `markProcessedIfAbsent` prevents duplicate processing

## Dead Letter Queue (DLQ)

Events that fail after `max-attempts` retries go to the DLQ:

- Stored in `dead_letter_events` table (JPA-backed, survives restarts)
- Viewable via dashboard
- Replayable via API
- Each replay increments `replay_count`

## Backpressure

- **Bounded queue** — `syncflow.runtime.sync.queue-capacity` (default 10000)
- **Circuit breaker** — `CircuitBreakerEventPublisher` pauses capture when destination is unhealthy
- **DLQ overflow** — Events exceeding retry count are routed to DLQ, not dropped

## Exactly-Once Semantics

CDC provides at-least-once delivery. SyncFlow achieves effective exactly-once via:

1. **Idempotent writes** — `markProcessedIfAbsent` in `processed_events` table
2. **Offset commit** — Only advances offset after successful write
3. **Transactional boundary** — Write + offset commit in same transaction where possible

## Kafka Transport

When `syncflow.kafka.enabled=true`, CDC events are published to Kafka topics instead of in-memory queues. This enables:

- Cross-pod event distribution
- Event replay from Kafka
- Decoupled producers/consumers

Topics are named `{prefix}.{pipelineId}` with configurable partitions and replication.
