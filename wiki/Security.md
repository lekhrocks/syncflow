# Security

## Authentication

### JWT Tokens

```bash
POST /api/auth/login
{
  "username": "admin",
  "password": "admin-test-password"
}
```

Response:
```json
{
  "token": "eyJhbGciOi...",
  "mustChangePassword": false
}
```

Include in subsequent requests:
```
Authorization: Bearer eyJhbGciOi...
```

### Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_JWT_SECRET` | HMAC signing secret (>= 32 bytes) | *(required)* |
| `SYNCFLOW_JWT_ISSUER` | Token issuer claim | `syncflow` |
| `SYNCFLOW_JWT_EXPIRY_MINUTES` | Token lifetime | `60` |

### Password Policy

- BCrypt hashing
- `mustChangePassword` flag for first login
- `POST /api/auth/change-password` for password rotation

## Authorization (RBAC)

Roles and permissions managed via `AuthorizationService`:

| Permission | Scope |
|------------|-------|
| `CONNECTION_READ` | View connections |
| `CONNECTION_WRITE` | Create/update connections |
| `CONNECTION_DELETE` | Delete connections |
| `PIPELINE_READ` | View pipelines |
| `PIPELINE_WRITE` | Create/update pipelines |
| `PIPELINE_DELETE` | Delete pipelines |
| `PIPELINE_EXECUTE` | Start snapshots/CDC |
| `ORG_READ` | View organization |
| `ORG_WRITE` | Manage organization |
| `AI_USE` | Access AI copilot |
| `AUDIT_READ` | View audit records |
| `APIKEY_REVOKE` | Revoke API keys |
| `EXECUTION_READ` | View execution logs |

## Credential Encryption

All stored credentials are encrypted with AES-256:

```bash
SYNCFLOW_ENCRYPTION_KEY=<base64-encoded-16/24/32-byte-key>
```

The `EncryptionService` handles encrypt/decrypt transparently. Credentials are never stored in plaintext.

## API Keys

```bash
# Issue API key
POST /api/admin/apikeys
{
  "name": "ci-pipeline",
  "scope": "PIPELINE_READ,PIPELINE_EXECUTE",
  "expiresAt": "2026-12-31T23:59:59Z"
}

# Use API key
X-Api-Key: sf_key_abc123...

# Revoke
DELETE /api/admin/apikeys/{id}
```

Keys are hashed (SHA-256) before storage. Only the prefix is returned on creation.

## Agent Authentication

Agents authenticate via token header:

```
X-Agent-Token: <agent-token>
```

Agent tokens are validated by `AgentTokenFilter` before reaching controllers.

## Public Paths

These endpoints do not require authentication:

```
/api/health/**
/api/auth/**
/api/agents/register
/api/agents/heartbeat
/actuator/**
/v3/api-docs/**
/swagger-ui/**
/graphiql/**
```

## Network Security

- Kubernetes `NetworkPolicy` manifests restrict pod-to-pod communication
- TLS termination at ingress (NGINX)
- No sensitive data in logs (credentials masked)
