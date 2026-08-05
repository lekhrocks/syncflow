# Kafka Integration

> **Status:** Operational — optional feature, off by default  
> **Default:** in-memory publisher (`BoundedQueueEventPublisher`); Kafka off unless enabled

SyncFlow ships Kafka as an **optional** event transport for the CDC pipeline. By default the CDC engine publishes change events through an in-memory bounded queue (`BoundedQueueEventPublisher`) so a single binary works with no external dependencies. Setting `syncflow.kafka.enabled=true` switches the CDC → sync path to Kafka: change events are written to Kafka topics and consumed back by the sync engine.

## When to use Kafka

| Use case | Recommendation |
|----------|----------------|
| Single-node / small team deployment | Keep the default in-memory publisher. No Kafka cluster needed. |
| Enterprise scale / horizontal consumers | Enable Kafka. Decouples capture from transform/write, allows multiple consumers, survives producer restarts. |
| Multi-pipeline fan-out to many writers | Enable Kafka. One topic per source table, per pipeline. |

See [ADR-003: Event Publisher Abstraction](../adr/ADR-003-event-stream-abstraction.md) for the design rationale.

## Enabling

Set in `application.yml` (or an env var):

```yaml
syncflow:
  kafka:
    enabled: true
    bootstrap-servers: localhost:9092
```

When `enabled: true`:
- `KafkaTopicProvisioner` creates a topic per source table before capture starts
- `KafkaEventPublisher` replaces the in-memory publisher for the pipeline
- `KafkaCdcConsumer` is activated and polls events back into `SyncOrchestrator`

When `enabled: false` (or unset), none of the Kafka beans activate — the app runs entirely in-memory.

## Topic naming

Each source table gets its own topic:

```
{prefix}.{pipelineId}.{table}
```

- Default prefix: `syncflow`
- Example: `syncflow.p-abc123.users`
- Unsupported characters in pipeline/table names are replaced with `_`

Topics are created with the configured partition count and replication factor, and tagged with `retention.ms` (default 7 days) and `cleanup.policy=delete`. Topic creation is idempotent — existing topics are silently skipped. Topics are deleted when a pipeline is deleted.

## End-to-end flow

```
Source DB (CDC)
   │  Debezium captures INSERT/UPDATE/DELETE
   ▼
KafkaEventPublisher ──► [topic {prefix}.{pipelineId}.{table}] ──► KafkaCdcConsumer
   │  JSON value, PK-keyed partition,                          │  polls {prefix}.{pipelineId}.*
   │  eventId/operation/pipelineId/table headers               ▼
                                                        SyncOrchestrator
                                                          (transform + write)
```

- **Key:** the row's primary key serialized as JSON (or the event ID if no PK) — guarantees events for the same row land on the same partition, preserving order.
- **Value:** the full `CDCEvent` as JSON.
- **Headers:** `eventId`, `operation`, `pipelineId`, `table`.
- **Delivery:** idempotent producer (`enable.idempotence=true`), `acks=all`, manual consumer commit after batch (`enable.auto.commit=false`), consumer isolation level `read_committed`.
- **Ordering:** per-row events are ordered per partition; each pipeline consumes from its own group.

## Configuration reference (`syncflow.kafka.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `kafka.enabled` | `false` | Master switch for the whole Kafka path. |
| `kafka.bootstrap-servers` | `localhost:9092` | Kafka broker(s). |
| `kafka.topic.prefix` | `syncflow` | Prefix prepended to every topic name. |
| `kafka.topic.partitions` | `3` | Partition count for created topics. |
| `kafka.topic.replication-factor` | `1` | Topic replication factor. |
| `kafka.topic.retention-ms` | `604800000` (7 days) | Topic retention window. |
| `kafka.producer.acks` | `all` | Producer acknowledgement level. |
| `kafka.producer.retries` | `3` | Producer retry count. |
| `kafka.producer.batch-size` | `65536` | Producer batch size in bytes. |
| `kafka.producer.linger-ms` | `5` | Producer batching linger. |
| `kafka.producer.compression-type` | `snappy` | Record compression. |
| `kafka.consumer.group-id` | `syncflow-consumer` | Base consumer group; `-{pipelineId}` is appended per pipeline. |
| `kafka.consumer.auto-offset-reset` | `earliest` | Where to start when no committed offset. |
| `kafka.consumer.enable-auto-commit` | `false` | Recommended `false` — manual commit after batch. |
| `kafka.consumer.max-poll-records` | `500` | Records per poll batch. |

**Env-var overrides:** every key maps to an env var via relaxed binding, e.g. `SYNCFLOW_KAFKA_ENABLED=true`, `SYNCFLOW_KAFKA_BOOTSTRAP_SERVERS=...`. See `application.yml` under `syncflow.kafka`.

## Probing / monitoring

Metrics (Micrometer, exposed via `/actuator/prometheus` when enabled):

| Metric | Meaning |
|--------|---------|
| `syncflow.kafka.publish.success` | Events written to Kafka (tagged by pipeline, topic). |
| `syncflow.kafka.publish.error` | Producer send failures. |
| `syncflow.kafka.serialize.error` | Event failed JSON serialization before publish. |
| `syncflow.kafka.consume.success` | Events consumed and submitted to sync (by pipeline, topic). |
| `syncflow.kafka.consume.error` | Records failed deserialize/submit on consume. |
| `syncflow.kafka.consumer.failure` | Consumer poll-loop failures. |

For a quick liveness check: `POST /api/pipelines/{id}/capture/start`, then confirm events flow by comparing `syncflow.kafka.publish.success` vs `syncflow.kafka.consume.success` for that pipeline.

## Known limitations

- No Kafka cluster is bundled with docker-compose or the local dev setup — you must run/point at your own brokers to use the feature.
- `publisher.publish` is asynchronous (fire-and-forget send); persistence guarantees come from the idempotent producer + `acks=all`, not from an explicit pipeline-level flush on each event.
- Replication factor `>1` requires a multi-broker cluster; the default `1` matches a single-broker dev setup.