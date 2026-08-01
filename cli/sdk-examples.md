# SyncFlow SDK Examples

## Java

```java
// build.gradle: implementation("com.syncflow.plugin:syncflow-client:0.1.0")

SyncFlowClient client = SyncFlowClient.builder()
    .endpoint("https://syncflow.example.com")
    .token(System.getenv("SYNCFLOW_TOKEN"))
    .build();

// List connections
List<Connection> connections = client.connections().list();
connections.forEach(c -> System.out.println(c.name()));

// Create pipeline
Pipeline pipe = client.pipelines().create(CreatePipelineRequest.builder()
    .name("users-sync")
    .sourceConnectionId("conn-1")
    .sourceSchema("public")
    .sourceTable("users")
    .destConnectionId("conn-2")
    .destSchema("public")
    .destTable("users_copy")
    .build());

// Start snapshot
client.pipelines().startSnapshot(pipe.id());
```

## Go

```go
package main

import (
    "context"
    "log"
    syncflow "github.com/syncflow/syncflow-go"
)

func main() {
    client := syncflow.NewClient(
        syncflow.WithEndpoint("https://syncflow.example.com"),
        syncflow.WithToken(os.Getenv("SYNCFLOW_TOKEN")),
    )

    ctx := context.Background()

    // List pipelines
    pipelines, err := client.Pipelines.List(ctx)
    if err != nil {
        log.Fatal(err)
    }

    // Create connection
    conn, err := client.Connections.Create(ctx, &syncflow.CreateConnectionRequest{
        Name:           "my-pg",
        ConnectionType: "POSTGRESQL",
        Host:           "pg.example.com",
        Port:           5432,
        Database:       "analytics",
        Username:       "reader",
        Password:       os.Getenv("PG_PASSWORD"),
    })
}

// Pagination
func listAllPipelines(ctx context.Context, client *syncflow.Client) ([]*syncflow.Pipeline, error) {
    var all []*syncflow.Pipeline
    cursor := ""
    for {
        page, err := client.Pipelines.List(ctx, syncflow.WithCursor(cursor), syncflow.WithLimit(50))
        if err != nil { return nil, err }
        all = append(all, page.Items...)
        if page.NextCursor == "" { break }
        cursor = page.NextCursor
    }
    return all, nil
}
```

## Python

```python
from syncflow import SyncFlowClient

client = SyncFlowClient(
    endpoint="https://syncflow.example.com",
    token=os.environ["SYNCFLOW_TOKEN"],
)

# List all connections with pagination
connections = []
cursor = None
while True:
    page = client.connections.list(cursor=cursor, limit=50)
    connections.extend(page.items)
    if not page.next_cursor:
        break
    cursor = page.next_cursor

# AI Copilot
response = client.ai.chat("Why is my pipeline slow?")
print(response.message)

# Create pipeline with streaming
pipeline = client.pipelines.create(
    name="users-sync",
    source_connection_id="pg-conn",
    source_schema="public",
    source_table="users",
    dest_connection_id="mongo-conn",
    dest_schema="admin",
    dest_table="users",
    sync_mode="CDC_SNAPSHOT_AND_INCREMENTAL",
)
```

## TypeScript

```typescript
import { SyncFlowClient } from "@syncflow/sdk";

const client = new SyncFlowClient({
  endpoint: "https://syncflow.example.com",
  token: process.env.SYNCFLOW_TOKEN,
});

// List with pagination
async function* listAll() {
  let cursor: string | undefined;
  do {
    const page = await client.pipelines.list({ cursor, limit: 50 });
    yield* page.items;
    cursor = page.nextCursor;
  } while (cursor);
}

for await (const pipeline of listAll()) {
  console.log(pipeline.name, pipeline.status);
}

// Error handling with retries
try {
  await client.pipelines.triggerSnapshot("pipeline-1");
} catch (err) {
  if (err instanceof SyncFlowRateLimitError) {
    await sleep(err.retryAfter);
    return await client.pipelines.triggerSnapshot("pipeline-1");
  }
  throw err;
}
```

## CLI

```bash
# Authenticate
syncflow login

# Create and deploy a pipeline
syncflow pipeline create "users-sync"
syncflow pipeline deploy "pipeline-id"

# Monitor
syncflow status
syncflow metrics
syncflow pipeline list

# Agent management
syncflow agent register "worker-01"
syncflow agent list

# Logs
syncflow logs "pipeline-id"
```
