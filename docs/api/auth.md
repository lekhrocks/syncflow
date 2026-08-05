# Authentication API

> **Stability:** 🌐 Public  
> **Base path:** `/api/auth`  
> **Content-Type:** `application/json`

JWT bearer authentication. Login with credentials to obtain a token; send it as
`Authorization: Bearer <token>` on protected endpoints. Public endpoints
(`/api/health`, `/api/auth/**`, actuator, swagger) need no token.

## POST /api/auth/login

Authenticates credentials and returns a JWT. The token's `scope` claim carries
the user's roles.

### Request

```json
{
  "username": "admin",
  "password": "admin-test-password"
}
```

### Response — 200 OK

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer"
}
```

### Errors

| Status | Body | When |
|--------|------|------|
| `401` | `{ "error": "invalid credentials" }` | Bad username/password, or the account is disabled/locked. |
| `400` | — | Malformed JSON body. |

## GET /api/auth/me

Returns the authenticated caller's own user record. Requires a bearer token.

### Response — 200 OK

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "username": "admin",
  "email": "admin@syncflow.local",
  "roles": "ADMIN",
  "enabled": true
}
```

### Errors

| Status | When |
|--------|------|
| `401` | Missing/invalid bearer token. |

## Using the token

```http
GET /api/users
Authorization: Bearer <token>
```

- Token is an **HS256** JWT signed with the configured `syncflow.jwt.secret`.
- Expiry is `syncflow.jwt.expiry-minutes` (default 60).
- Tokens are stateless — no server-side session.

## Configuration (`syncflow.jwt.*`)

| Key | Default | Env | Description |
|-----|---------|-----|-------------|
| `syncflow.jwt.secret` | dev default | `SYNCFLOW_JWT_SECRET` | Base64-encoded HS256 key, **≥ 32 bytes**. Required. |
| `syncflow.jwt.issuer` | `syncflow` | `SYNCFLOW_JWT_ISSUER` | JWT `iss` claim. |
| `syncflow.jwt.expiry-minutes` | `60` | `SYNCFLOW_JWT_EXPIRY_MINUTES` | Token lifetime. |

> **Security:** replace the default secret in production. Startup fails with a
> clear message if the secret is missing, not base64, or shorter than 256 bits.
