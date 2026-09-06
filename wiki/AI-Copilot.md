# AI Copilot

SyncFlow includes an LLM-powered AI copilot for pipeline assistance, review, and optimization.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ai/chat` | Chat with AI copilot |
| `POST` | `/api/ai/plan` | Create reasoning plan |
| `POST` | `/api/ai/analyze` | Multi-agent analysis |
| `POST` | `/api/ai/document` | Search knowledge base |
| `POST` | `/api/ai/review` | AI pipeline review |
| `POST` | `/api/ai/recommend` | Optimization recommendations |
| `GET` | `/api/ai/history` | Conversation history |

## Features

### Chat

Conversational interface for pipeline questions:

```json
POST /api/ai/chat
{
  "message": "How do I set up CDC from PostgreSQL to MySQL?",
  "sessionId": "optional-session-id"
}
```

### Pipeline Review

AI analyzes a pipeline design and suggests improvements:

```json
POST /api/ai/review
{
  "pipelineId": "pipeline-uuid"
}
```

Review covers:
- Column type compatibility
- Missing transformations
- Performance bottlenecks
- Security concerns
- Best practices

### Optimization Recommendations

```json
POST /api/ai/recommend
{
  "pipelineId": "pipeline-uuid",
  "metrics": {
    "throughput": 1000,
    "latency": 50,
    "errorRate": 0.01
  }
}
```

### Knowledge Base Search

Semantic search across pipeline documentation:

```json
POST /api/ai/document
{
  "query": "how to handle schema changes in CDC"
}
```

## Configuration

```yaml
syncflow:
  ai:
    endpoint: ${SYNCFLOW_AI_ENDPOINT:https://api.openai.com/v1/chat/completions}
    model: ${SYNCFLOW_AI_MODEL:gpt-4o}
    api-key: ${SYNCFLOW_AI_API_KEY:}
    max-tokens: 4096
    temperature: 0.3
```

## Authentication

AI endpoints require `AI_USE` permission. Check via `AuthorizationService`.

## Privacy

- Prompts are not stored permanently
- Conversation history is session-scoped
- No sensitive data (credentials, tokens) included in prompts
