# API Reference

Base URL: `http://localhost:8080`

All endpoints except auth require `Authorization: Bearer <jwt-token>` header.

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI docs: `http://localhost:8080/v3/api-docs`

---

## Authentication

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/api/auth/login` | Login (returns JWT) | No |
| `POST` | `/api/auth/change-password` | Change password | Yes |
| `GET` | `/api/auth/me` | Current user info | Yes |

---

## Connections

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/connections` | Create connection |
| `GET` | `/api/connections` | List connections |
| `GET` | `/api/connections/{id}` | Get connection |
| `PUT` | `/api/connections/{id}` | Update connection |
| `DELETE` | `/api/connections/{id}` | Delete connection |
| `POST` | `/api/connections/test` | Test connection |
| `GET` | `/api/connections/{id}/health` | Health check |

---

## Metadata Discovery

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/connections/{id}/metadata` | Discover schemas |
| `GET` | `/api/connections/{id}/schemas/{schema}/tables` | Discover tables |
| `GET` | `/api/connections/{id}/schemas/{schema}/tables/{table}/columns` | Discover columns |
| `GET` | `/api/connections/{id}/schemas/{schema}/tables/{table}/indexes` | Discover indexes |
| `GET` | `/api/connections/{id}/schemas/{schema}/tables/{table}/constraints` | Discover constraints |
| `POST` | `/api/connections/{id}/metadata/refresh` | Refresh cache |

---

## Pipelines

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines` | Create pipeline |
| `GET` | `/api/pipelines` | List pipelines |
| `GET` | `/api/pipelines/{id}` | Get pipeline |
| `PUT` | `/api/pipelines/{id}` | Update pipeline |
| `DELETE` | `/api/pipelines/{id}` | Delete pipeline |
| `POST` | `/api/pipelines/{id}/validate` | Validate pipeline |
| `POST` | `/api/pipelines/{id}/rollback` | Rollback to version |
| `GET` | `/api/pipelines/{id}/versions` | List versions |
| `GET` | `/api/pipelines/{id}/preview` | Preview output |
| `GET` | `/api/pipelines/{id}/conflicts` | Detect conflicts |

---

## Snapshots

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines/{id}/snapshot` | Start snapshot |
| `GET` | `/api/snapshots` | List snapshots |
| `GET` | `/api/snapshots/{id}` | Get snapshot |
| `GET` | `/api/snapshots/{id}/progress` | Get progress |
| `GET` | `/api/snapshots/{id}/events` | SSE progress stream |
| `POST` | `/api/snapshots/{id}/cancel` | Cancel snapshot |

---

## CDC Capture

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/pipelines/{id}/capture/start` | Start CDC |
| `POST` | `/api/pipelines/{id}/capture/stop` | Stop CDC |
| `POST` | `/api/pipelines/{id}/capture/pause` | Pause CDC |
| `POST` | `/api/pipelines/{id}/capture/resume` | Resume CDC |
| `GET` | `/api/pipelines/{id}/capture/status` | Capture status |

---

## Agent Fleet

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/agents/register` | Register agent |
| `POST` | `/api/agents/heartbeat` | Agent heartbeat |
| `GET` | `/api/agents` | List agents |
| `GET` | `/api/agents/{id}` | Get agent |
| `POST` | `/api/agents/{id}/drain` | Drain agent |
| `POST` | `/api/agents/{id}/restart` | Restart agent |
| `GET` | `/api/agents/{id}/metrics` | Agent metrics |

---

## Plugins

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/plugins` | List plugins |
| `GET` | `/api/plugins/{id}` | Get plugin |
| `POST` | `/api/plugins/install` | Install plugin |
| `POST` | `/api/plugins/{id}/enable` | Enable plugin |
| `POST` | `/api/plugins/{id}/disable` | Disable plugin |
| `DELETE` | `/api/plugins/{id}` | Uninstall plugin |
| `GET` | `/api/plugins/{id}/capabilities` | Plugin capabilities |

---

## AI Copilot

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ai/chat` | Chat with copilot |
| `POST` | `/api/ai/plan` | Create reasoning plan |
| `POST` | `/api/ai/analyze` | Multi-agent analysis |
| `POST` | `/api/ai/document` | Search knowledge base |
| `POST` | `/api/ai/review` | Pipeline review |
| `POST` | `/api/ai/recommend` | Optimization recommendations |
| `GET` | `/api/ai/history` | Conversation history |

---

## Admin & Multi-Tenancy

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/admin/organizations` | Create organization |
| `POST` | `/api/admin/workspaces` | Create workspace |
| `POST` | `/api/admin/projects` | Create project |
| `POST` | `/api/admin/apikeys` | Issue API key |
| `DELETE` | `/api/admin/apikeys/{id}` | Revoke API key |
| `GET` | `/api/admin/quotas` | Get tenant quota |
| `GET` | `/api/admin/audit` | List audit records |
| `GET` | `/api/admin/tenants` | Current tenant context |

---

## Dashboard & Diagnostics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/dashboard/overview` | Dashboard overview |
| `GET` | `/api/dashboard/pipelines` | Pipeline dashboard |
| `GET` | `/api/dashboard/connections` | Connection dashboard |
| `GET` | `/api/dashboard/connectors` | Connector dashboard |
| `GET` | `/api/dashboard/jobs` | Job dashboard |
| `GET` | `/api/dashboard/metrics` | Metrics dashboard |
| `GET` | `/api/dashboard/errors` | Error dashboard |
| `GET` | `/api/diagnostics/system` | System diagnostics |
| `GET` | `/api/diagnostics/connectors` | Connector diagnostics |
| `GET` | `/api/diagnostics/pipelines` | Pipeline diagnostics |
| `GET` | `/api/diagnostics/executions` | Execution diagnostics |

---

## Health & Operations

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/health` | Health check | No |
| `GET` | `/actuator/health` | Spring health | No |
| `GET` | `/actuator/prometheus` | Prometheus metrics | No |
| `GET` | `/actuator/metrics` | Micrometer metrics | No |
| `POST` | `/actuator/shutdown` | Graceful shutdown | No |
