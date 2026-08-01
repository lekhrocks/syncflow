# C4 Level 2: Container Diagram

```mermaid
C4Container
    title Container diagram for SyncFlow Control Plane

    Person(user, "Platform User", "Data engineer or operator")

    System_Boundary(cp, "SyncFlow Control Plane") {
        Container(api, "REST API", "Spring Boot 3.5", "Handles all HTTP requests: pipelines, connections, metadata, sync, AI, admin")
        Container(graphql, "GraphQL API", "Spring GraphQL", "Read-only query interface for pipelines and events")
        Container(ui, "React UI", "React 19 + Mantine", "Web console for managing platform")
        ContainerDb(meta_db, "PostgreSQL", "PostgreSQL 16", "Stores pipelines, connections, users, audit logs, workflow state")
        ContainerDb(cache, "Redis", "Redis 7", "Caching, rate limiting, session store (future)")
        Container(agent_service, "Agent Service", "Spring Boot", "Manages agent fleet: registration, heartbeat, task assignment")
        Container(auth_service, "Auth Service", "Spring Security", "OIDC/OAuth2/JWT authentication and RBAC authorization")
        Container(ai_service, "AI Copilot", "Spring Boot", "AI agents, prompt builder, knowledge base, LLM client")
        Container(workflow, "Workflow Engine", "Spring Boot", "DAG scheduler, task queue, leader election, worker management")
    }

    System_Ext(source_db, "Source Database", "PostgreSQL / MySQL / MongoDB")
    System_Ext(dest_db, "Destination Database", "PostgreSQL / MySQL / MongoDB / Redis")
    System_Ext(agent_process, "SyncFlow Agent", "Java 25 process in customer VPC")
    System_Ext(llm, "LLM API", "OpenAI / Azure / Anthropic")
    System_Ext(idp, "OIDC Provider", "Keycloak / Azure AD")

    Rel(user, ui, "Uses", "HTTPS")
    Rel(user, api, "Uses", "HTTPS/REST")
    Rel(user, graphql, "Uses", "HTTPS/GraphQL")
    Rel(ui, api, "Calls", "REST")
    Rel(api, meta_db, "Reads/Writes", "JDBC")
    Rel(api, agent_service, "Delegates", "Spring DI")
    Rel(api, auth_service, "Validates", "Spring Security")
    Rel(api, ai_service, "Delegates", "Spring DI")
    Rel(api, workflow, "Triggers", "Spring DI")
    Rel(api, source_db, "Reads metadata", "JDBC")
    Rel(api, dest_db, "Writes data", "JDBC")
    Rel(agent_service, agent_process, "Registers/Heartbeat", "HTTPS mTLS")
    Rel(ai_service, llm, "Generates", "OpenAI-compatible API")
    Rel(auth_service, idp, "Validates tokens", "OIDC")
    Rel(workflow, meta_db, "Persists state", "JDBC")
```
