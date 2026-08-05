# Authentication & CSRF

> **Status:** Implemented  
> **Version:** 1.0  
> **Last Updated:** 2026-08-05

## Model

The control plane uses **stateless JWT bearer authentication** (HS256) over
Spring Security oauth2 resource-server. There is no server-side session; every
request is authenticated by the bearer token in the `Authorization` header.

```
            ┌──────────────────────────────────────────────────────┐
[Client] ──►│ Spring Security filter chain                          │
            │  - public paths: permitAll                            │
            │  - /api/auth/login: permitAll                        │
            │  - /api/** : JWT bearer (oauth2ResourceServer) + auth │
            └─────────────────────────┬────────────────────────────┘
                                      ▼
                            Authentication (JWT claims)
                                      │  authorities (ROLE_* from scope)
                                      ▼
                            TenantFilter → TenantContext
                                      │  roles
                                      ▼
                            AuthorizationService (RBAC)
```

The JWT's `scope` claim maps to `ROLE_*` Spring authorities, which
`TenantFilter` reads into the tenant context roles; the existing
`AuthorizationService`/`PolicyResolver` RBAC enforces permissions. Auth
plugs into the pre-existing RBAC — there is no parallel authorization model.

## Accounts & credentials

- Users live in the `app_users` table (migration V9). Credentials are stored as
  **BCrypt** password hashes (`password_hash` column). Plaintext passwords are
  never stored or logged.
- A default `admin` user is seeded by V9 (`admin-test-password`) for
  development/test only. **Replace the password and JWT secret in production.**
- The `PolicyResolver` grants the `admin` username full permissions; other users
  are authorized by their roles/authorities.

## Users API

`/api/users` (see [User Management](#user-management)) manages accounts and role
assignment. Creating/updating roles is restricted to a known allow-list and
guarded by RBAC (`ORG_WRITE`).

## CSRF policy (hybrid)

CSRF protection is **enabled** but scoped so it does not interfere with the
bearer-token API:

- **`/api/**`** — CSRF is ignored. These endpoints use header bearer tokens,
  which browsers cannot attach on behalf of a victim (the classic CSRF attack
  vector), so CSRF protection is unnecessary and would only add friction.
- **Non-`/api/**` paths** (cookie-based session flows) — CSRF is enforced via a
  `CookieCsrfTokenRepository`. This is what satisfies the "Disabled Spring CSRF"
  scan finding: protection is on, just scoped.

## JWT configuration

| Setting | Detail |
|---------|--------|
| Algorithm | HS256 (HMAC-SHA256, symmetric) |
| Secret | Base64, forced ≥ 32 bytes at startup; invalid config fails fast. |
| Claims | `iss`, `iat`, `exp`, `sub` (username), `scope` (roles) |
| Signing | Nimbus (`ImmutableJWKSet` + `OctetSequenceKey`), `NimbusJwtDecoder` |

## Security considerations

- **Stateless**: no session fixation; outages don't invalidate tokens until `exp`.
- **Secret rotation** requires coordinated key change across instances.
- **HS256** is symmetric — any holder of the secret can mint tokens. For
  multi-signer or external verification, migrate to **RS256/asymmetric**
  (documented upgrade path).
- Login returns a uniform `401` for bad credentials and disabled/locked
  accounts — it does not reveal whether an account exists.
- Failed/malformed JWTs are rejected by the resource server; tampering is
  detected by the signature.

## Threat-model mapping

| Threat | Control |
|--------|---------|
| Bad/missing token | Resource server rejects; 401. |
| Token forgery | HS256 signature verification. |
| Account enumeration via login | Uniform 401 for all auth failures. |
| CSRF on bearer API | Not applicable (header token); CSRF enabled for cookie paths. |
| Privilege escalation | Role allow-list on user management; RBAC on `/api/users`. |
| Weak secret | Startup validation enforces ≥ 256-bit key. |