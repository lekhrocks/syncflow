# Runbook: Agent Offline

> **Severity:** SEV1 — Data sync stops, CDC falls behind  
> **Owner:** Platform Engineering

## Symptoms
- `AgentOffline` alert firing (`syncflow_agents_online < 1`)
- `GET /api/agents` shows agent with status `OFFLINE` or `UNREACHABLE`
- Dashboard "Agent Fleet" card shows agents with red status
- No heartbeat received for > 60 seconds

## Possible Causes
1. Agent process crashed (OOM, unhandled exception)
2. Agent host/pod restarted (node failure, deployment rollout)
3. Network connectivity lost between agent and control plane
4. Agent TLS certificate expired (mTLS handshake failure)
5. Agent configuration changed (incorrect control plane URL)
6. Control plane unreachable from agent VPC (firewall rule change)

## Diagnosis

```bash
# Get agent details
curl -u admin:$TOKEN http://syncflow.example.com/api/agents/{id} | jq .

# Check last heartbeat
curl -u admin:$TOKEN http://syncflow.example.com/api/agents/{id} | jq '.lastHeartbeat'

# Verify agent reachability
nc -zv {agent_host} 9090

# Check agent logs via SSH (if accessible)
ssh {agent_host} "journalctl -u syncflow-agent --since '5 minutes ago' | tail -50"

# Check agent pod logs (K8s)
kubectl logs -n syncflow-agent -l app=syncflow-agent --tail=100

# Check network path
traceroute {agent_host}
```

## Recovery Steps

### Step 1: Identify the agent

```bash
# List all agents and their status
curl -u admin:$TOKEN http://syncflow.example.com/api/agents | jq '.[] | {id, hostname, status, region, lastHeartbeat}'
```

### Step 2: Restart the agent

```bash
# Kubernetes agent restart
kubectl rollout restart deployment syncflow-agent -n syncflow-agent

# Wait for restart
kubectl rollout status deployment syncflow-agent -n syncflow-agent --timeout=120s

# Verify registration
sleep 15  # Wait for heartbeat interval
curl -u admin:$TOKEN http://syncflow.example.com/api/agents | jq '.[] | {hostname, status}'
```

### Step 3: If restart doesn't work

```bash
# Force re-registration
# SSH into agent host and restart the process
ssh {agent_host} "sudo systemctl restart syncflow-agent"

# Verify agent re-registered
sleep 20
curl -u admin:$TOKEN http://syncflow.example.com/api/agents | jq '.[] | {hostname, status, lastHeartbeat}'
```

### Step 4: Verify agent capabilities

```bash
# Check agent has required capabilities
curl -u admin:$TOKEN http://syncflow.example.com/api/agents/{id} | jq '.capabilities'

# Should include: SNAPSHOT, CDC, SYNCHRONIZATION, METADATA
```

## Escalation
- Agent offline > 5 minutes: page Platform Lead
- All agents offline in a region: SEV1, page Platform Lead + Infrastructure
- Certificate expiry > 24 hours: page Security Team

## Post-Incident
- [ ] Add agent health check with automated restart
- [ ] Review agent monitoring (heartbeat timeout tuning)
- [ ] Add certificate expiry monitoring (30-day warning)
- [ ] Document agent deployment and configuration
- [ ] Add redundant control plane endpoints for agent connectivity
