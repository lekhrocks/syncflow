# Snapshots

A snapshot performs the initial bulk load of data from a source to a destination. It uses parallel PK-range chunking for high throughput and checkpoints for resume-on-failure.

## How It Works

```
1. Estimate total rows across all mapped tables
2. Split each table into PK-range chunks (via rangeChunks())
3. Fork one StructuredTaskScope task per chunk
4. Each task:
   a. Reads a batch of rows (keyset pagination)
   b. Applies filter/transform pipeline
   c. Writes to destination (serialized on writerLock)
   d. Checkpoints every N batches
   e. Publishes progress via SSE
5. On completion: flush + commit (or rollback on cancel)
6. Durable state in snapshot_jobs table
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines/{id}/snapshot` | Start snapshot |
| `GET` | `/api/snapshots` | List snapshot jobs |
| `GET` | `/api/snapshots/{id}` | Get snapshot job |
| `GET` | `/api/snapshots/{id}/progress` | Get progress |
| `GET` | `/api/snapshots/{id}/events` | SSE live progress stream |
| `POST` | `/api/snapshots/{id}/cancel` | Cancel snapshot |

## Parallel Execution

Configured via `syncflow.runtime.snapshot`:

| Property | Default | Description |
|----------|---------|-------------|
| `parallelism` | 4 | Max concurrent chunk workers |
| `max-chunks` | 64 | Max chunks per table |
| `checkpoint-interval-batches` | 5 | Checkpoint frequency |
| `progress-publish-interval-batches` | 10 | SSE progress frequency |

Each worker gets its own connector clone (JDBC connections are not thread-safe). A 64-chunk table with 4 parallelism opens only 4 DB connections.

## Checkpoint and Resume

Every N batches, the snapshot persists a checkpoint:

```
SnapshotCheckpoint
  ├── pipelineId
  ├── sourceTable
  ├── chunkIndex
  ├── lastBatchNumber
  ├── rowsProcessed
  └── cursor (keyset pagination cursor)
```

On restart, each chunk resumes from its last checkpoint cursor instead of re-reading from the start.

## Cancellation

`POST /api/snapshots/{id}/cancel` sets a cancellation flag. The snapshot:

1. Checks the flag before each batch
2. Under `progressLock`, rolls back partial writes
3. Sets terminal status to CANCELLED (not FAILED)

This prevents duplicate rows on resume.

## Distributed Locking

`SnapshotExecutor.start()` acquires a Postgres advisory lock (`snapshot:{pipelineId}`) before starting. Two pods cannot start the same pipeline's snapshot concurrently.
