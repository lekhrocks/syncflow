# ADR-008: Why REST/HTTP for Agent Communication (not gRPC)?

## Status: Accepted

## Context
Control Plane and Data Plane agents need to exchange registration, heartbeat, task assignments, and metrics. Options: gRPC with Protocol Buffers, plain HTTP REST, or WebSocket streaming.

## Decision
Use REST/HTTP for agent communication. gRPC is deferred until streaming CDC event transport between agent and control plane is required.

## Rationale
- **Existing infrastructure**: The control plane already has REST endpoints for agent registration, heartbeat, and metrics. Adding gRPC would require a separate server, health checks, and port configuration.
- **Simple contract**: Agent communication is request-response (register → 200, heartbeat → OK, assign work → ACK). Not streaming.
- **Debuggability**: REST/JSON payloads are human-readable with `curl`. gRPC binary protobuf requires `grpcurl` or a schema registry.
- **Migration path**: When CDC event streaming is needed (events from agent → control plane), gRPC bi-directional streaming is the right choice. The agent module's HTTP client can be replaced with a gRPC client without changing the `FleetManager` contract.

## Consequences
- Current agent communication uses 6 REST endpoints (register, heartbeat, list, get, drain, metrics).
- All payloads are JSON — no schema compilation step.
- gRPC is a single-dependency addition (`grpc-netty-shaded`) when streaming is needed.

## Links
- `AgentController.java`, `AgentRegistrar.java`, `HeartbeatSender.java`
- Control plane / data plane communication is over HTTPS with mutual TLS (cert-manager)
