# API Stability Matrix

> **Version:** 1.0  
> **Classification Guide:**  
> 🔒 **Internal** — may change without notice  
> 🤝 **Partner** — change requires deprecation notice (90 days)  
> 🌐 **Public** — change requires deprecation notice (180 days) + migration guide  

---

## REST API

| Endpoint | Stability | Since | Deprecation | Notes |
|----------|:---------:|:-----:|:-----------:|-------|
| `GET /api/health` | 🌐 Public | 0.1.0 | — | |
| `GET /api/connections` | 🌐 Public | 0.1.0 | — | |
| `POST /api/connections` | 🌐 Public | 0.1.0 | — | |
| `GET /api/connections/{id}` | 🌐 Public | 0.1.0 | — | |
| `PUT /api/connections/{id}` | 🌐 Public | 0.1.0 | — | |
| `DELETE /api/connections/{id}` | 🌐 Public | 0.1.0 | — | |
| `POST /api/connections/test` | 🌐 Public | 0.1.0 | — | |
| `GET /api/connections/{id}/health` | 🌐 Public | 0.1.0 | — | |
| `GET /api/connections/{id}/metadata` | 🌐 Public | 0.1.0 | — | |
| `GET /api/connections/{id}/schemas/{s}/tables` | 🌐 Public | 0.1.0 | — | |
| `GET /api/connections/{id}/metadata/refresh` | 🌐 Public | 0.1.0 | — | |
| `GET /api/pipelines` | 🌐 Public | 0.1.0 | — | |
| `POST /api/pipelines` | 🌐 Public | 0.1.0 | — | [create/update reference](pipeline-create-update.md) |
| `GET /api/pipelines/{id}` | 🌐 Public | 0.1.0 | — | |
| `PUT /api/pipelines/{id}` | 🌐 Public | 0.1.0 | — | [create/update reference](pipeline-create-update.md) |
| `DELETE /api/pipelines/{id}` | 🌐 Public | 0.1.0 | — | |
| `POST /api/pipelines/{id}/validate` | 🌐 Public | 0.1.0 | — | |
| `POST /api/pipelines/{id}/snapshot` | 🌐 Public | 0.1.0 | — | |
| `POST /api/pipelines/{id}/capture/start` | 🌐 Public | 0.1.0 | — | |
| `POST /api/pipelines/{id}/sync/start` | 🌐 Public | 0.1.0 | — | |
| `GET /api/snapshots` | 🤝 Partner | 0.1.0 | — | |
| `GET /api/snapshots/{id}` | 🤝 Partner | 0.1.0 | — | |
| `GET /api/workflows` | 🔒 Internal | 0.1.0 | — | Workflow engine is platform-internal |
| `POST /api/workflows` | 🔒 Internal | 0.1.0 | — | |
| `GET /api/agents` | 🤝 Partner | 0.1.0 | — | Agent management for MSPs |
| `GET /api/agents/{id}` | 🤝 Partner | 0.1.0 | — | |
| `POST /api/agents/register` | 🤝 Partner | 0.1.0 | — | |
| `POST /api/ai/chat` | 🌐 Public | 0.1.0 | — | |
| `POST /api/ai/pipeline` | 🌐 Public | 0.1.0 | — | |
| `POST /api/auth/login` | 🌐 Public | 0.1.0 | — | [auth reference](auth.md) |
| `GET /api/auth/me` | 🌐 Public | 0.1.0 | — | [auth reference](auth.md) |
| `GET /api/users` | 🌐 Public | 0.1.0 | — | [auth reference](auth.md) |
| `GET /api/dashboard/overview` | 🔒 Internal | 0.1.0 | — | Dashboard is platform UI |
| `GET /api/diagnostics/system` | 🔒 Internal | 0.1.0 | — | |
| `GET /api/plugins` | 🤝 Partner | 0.1.0 | — | Plugin marketplace |
| `POST /api/plugins/install` | 🤝 Partner | 0.1.0 | — | |
| `GET /api/admin/quotas` | 🔒 Internal | 0.1.0 | — | Admin console |

## SDK Compatibility

| SDK | Status | Version | Notes |
|-----|:------:|:-------:|-------|
| Java (`syncflow-client`) | 🟢 Active | 0.1.0 | Bundled with platform |
| Go (`syncflow-go`) | 🟡 Beta | 0.1.0-beta | Generated from OpenAPI |
| Python (`syncflow-py`) | 🟡 Beta | 0.1.0-beta | Generated from OpenAPI |
| TypeScript (`@syncflow/sdk`) | 🟡 Beta | 0.1.0-beta | Published to npm |

## Plugin API

| Contract | Stability | Since | Notes |
|----------|:---------:|:-----:|-------|
| `PluginConnector` SPI | 🤝 Partner | 0.1.0 | |
| `SnapshotProvider` SPI | 🤝 Partner | 0.1.0 | |
| `CdcProvider` SPI | 🤝 Partner | 0.1.0 | |
| `DestinationWriterProvider` SPI | 🤝 Partner | 0.1.0 | |
| `PluginDescriptor` | 🌐 Public | 0.1.0 | |
| `ConfigurationSchema` | 🌐 Public | 0.1.0 | |
