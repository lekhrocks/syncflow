# Configuration

## Environment Variables

All configuration is externalizable via environment variables. The pattern is `SYNCFLOW_<SECTION>_<KEY>`.

### Required

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_ENCRYPTION_KEY` | Base64-encoded AES key (16/24/32 bytes) for credential encryption | *(none — fails fast if missing)* |
| `SYNCFLOW_JWT_SECRET` | Base64-encoded HMAC secret (>= 32 bytes) for JWT signing | *(none — fails fast if missing)* |

### Application

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_JWT_ISSUER` | JWT issuer claim | `syncflow` |
| `SYNCFLOW_JWT_EXPIRY_MINUTES` | JWT token expiry in minutes | `60` |

### Runtime Tunables

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_RUNTIME_SYNC_QUEUE_CAPACITY` | Per-pipeline CDC event queue capacity | `10000` |
| `SYNCFLOW_RUNTIME_SYNC_BATCH_SIZE` | Max events drained + written per batch | `100` |
| `SYNCFLOW_RUNTIME_SYNC_POLL_TIMEOUT` | Poll timeout when queue is empty | `500ms` |
| `SYNCFLOW_RUNTIME_SNAPSHOT_CHECKPOINT_INTERVAL_BATCHES` | Checkpoint every N batches | `5` |
| `SYNCFLOW_RUNTIME_RETRY_MAX_ATTEMPTS` | Max delivery attempts before DLQ | `3` |
| `SYNCFLOW_RUNTIME_RETRY_BASE_DELAY` | Exponential backoff base delay | `1000ms` |

### Kafka (Optional)

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_KAFKA_ENABLED` | Enable Kafka transport | `false` |
| `SYNCFLOW_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker list | `localhost:9092` |
| `SYNCFLOW_KAFKA_TOPIC_PREFIX` | Topic name prefix | `syncflow` |

### Agent

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_AGENT_CONTROL_PLANE` | Control plane URL | `http://localhost:8080` |
| `SYNCFLOW_AGENT_VERSION` | Agent version string | `0.1.0` |

### AI Copilot

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_AI_ENDPOINT` | LLM API endpoint | `https://api.openai.com/v1/chat/completions` |
| `SYNCFLOW_AI_MODEL` | LLM model name | `gpt-4o` |
| `SYNCFLOW_AI_API_KEY` | LLM API key | *(empty)* |
| `SYNCFLOW_AI_MAX_TOKENS` | Max tokens per request | `4096` |
| `SYNCFLOW_AI_TEMPERATURE` | Sampling temperature | `0.3` |

## Spring Properties

Key `application.yml` properties:

```yaml
spring:
  threads:
    virtual:
      enabled: true          # Virtual threads for Tomcat
  datasource:
    url: jdbc:postgresql://localhost:5432/syncflow
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,shutdown
```

## RuntimeProperties

Bound to `@ConfigurationProperties("syncflow.runtime")` with `@Validated`. Constructor injection of `ActiveCaptureRepository` and `DistributedLockService`.

```yaml
syncflow:
  runtime:
    sync:
      queue-capacity: 10000
      batch-size: 100
      poll-timeout: 500ms
    snapshot:
      checkpoint-interval-batches: 5
      progress-publish-interval-batches: 10
      parallelism: 4
      max-chunks: 64
    retry:
      max-attempts: 3
      base-delay: 1000ms
```

## Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local development with PostgreSQL |
| `test` | Integration tests with Testcontainers |
| `production` | Hardened settings for deployment |
