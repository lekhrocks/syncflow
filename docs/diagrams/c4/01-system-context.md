# C4 Level 1: System Context

```mermaid
C4Context
    title System Context diagram for SyncFlow Enterprise CDC Platform

    Person(user, "Platform User", "Data engineer or operator managing pipelines")
    Person(admin, "Platform Admin", "System administrator managing tenants and agents")
    System(syncflow, "SyncFlow Control Plane", "Centralized management API, UI, orchestration")

    System_Ext(source_db, "Source Database", "PostgreSQL, MySQL, or MongoDB")
    System_Ext(dest_db, "Destination Database", "PostgreSQL, MySQL, MongoDB, or Redis")
    System_Ext(ai_llm, "AI LLM Provider", "OpenAI-compatible API for AI Copilot")
    System_Ext(oidc, "OIDC Provider", "Keycloak / Azure AD / Okta for authentication")
    System_Ext(observability, "Observability Stack", "Prometheus + Grafana + Loki + Tempo")
    System_Ext(s3, "Backup Storage", "S3-compatible object store for backups")

    System_Ext(agent, "SyncFlow Agent", "Data plane running in customer VPC")

    Rel(user, syncflow, "Uses", "HTTPS/REST")
    Rel(admin, syncflow, "Administers", "HTTPS/REST")
    Rel(syncflow, source_db, "Reads schema & data", "JDBC/MongoDB Wire")
    Rel(syncflow, dest_db, "Writes synchronized data", "JDBC/MongoDB Wire")
    Rel(syncflow, ai_llm, "Generates suggestions", "OpenAI-compatible API")
    Rel(syncflow, oidc, "Authenticates users", "OIDC/OAuth2")
    Rel(syncflow, observability, "Exports metrics & traces", "OTLP/Prometheus")
    Rel(syncflow, s3, "Stores backups", "S3 API")
    Rel(agent, source_db, "Reads data", "JDBC/ChangeStreams")
    Rel(agent, dest_db, "Writes data", "JDBC/MongoDB Wire")
    Rel(agent, syncflow, "Reports heartbeat & metrics", "HTTPS/REST mTLS")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="2")
```
