# Agent / Data Plane

The agent module is a standalone Spring Boot application that runs in the data plane, executing snapshot and CDC operations close to the databases.

## Architecture

```
Control Plane (syncflow-api :8080)
    |
    +-- POST /api/agents/register (agent registers on startup)
    +-- POST /api/agents/heartbeat (every 15s, with HW metrics)
    +-- GET /api/agents (list all agents)
    +-- POST /api/agents/{id}/drain (drain agent)
    +-- POST /api/agents/{id}/restart (restart agent)
    |
Data Plane (syncflow-agent :9090)
    ├── AgentRegistrar (registers with control plane)
    └── HeartbeatSender (sends hardware metrics)
```

## Running the Agent

```bash
# Build
./gradlew :syncflow-agent:bootRun

# Or with Docker
docker build -f docker/Dockerfile.agent -t syncflow-agent .
docker run -e SYNCFLOW_AGENT_CONTROL_PLANE=http://control-plane:8080 syncflow-agent
```

## Registration

On startup, the agent registers with the control plane:

```json
POST /api/agents/register
{
  "version": "0.1.0",
  "capabilities": ["SNAPSHOT", "CDC", "SYNCHRONIZATION", "METADATA"],
  "labels": {"type": "standard"},
  "environment": "customer",
  "region": "us-east-1",
  "hostname": "agent-1.example.com"
}
```

## Heartbeat

Every 15 seconds, the agent sends hardware metrics:

```json
POST /api/agents/heartbeat
{
  "agentId": "agent-uuid",
  "cpuPercent": 45.2,
  "memoryUsed": 2147483648,
  "memoryTotal": 4294967296,
  "runningJobs": 2
}
```

## Agent States

| State | Description |
|-------|-------------|
| `ONLINE` | Agent is registered and sending heartbeats |
| `DRAINING` | Agent is finishing current jobs, not accepting new ones |
| `OFFLINE` | Agent missed heartbeats (detected by control plane) |

## Fleet Management

`FleetManager` in the control plane manages the agent fleet:

- Tracks online/offline status
- Assigns jobs to agents based on capability and load
- Handles agent drain (move jobs before shutdown)
- Monitors hardware metrics for capacity planning

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNCFLOW_AGENT_CONTROL_PLANE` | Control plane URL | `http://localhost:8080` |
| `SYNCFLOW_AGENT_VERSION` | Agent version | `0.1.0` |

## Use Cases

1. **Remote Execution** — Run snapshots/CDC on machines close to source databases
2. **Load Distribution** — Spread workload across multiple agents
3. **Network Isolation** — Agents in VPC with database access; control plane in DMZ
4. **Edge Deployment** — Agents at edge locations syncing to central control plane
