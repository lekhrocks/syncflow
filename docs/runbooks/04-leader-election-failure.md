# Runbook: Leader Election Failure

> **Severity:** SEV1 — Workflow scheduling stopped, all pipelines stalled  
> **Owner:** Platform Engineering  

## Symptoms
- `WorkflowScheduler.isLeader()` returns false on all pods
- `isLeaderAlive()` returns false for > 30 seconds
- Workflows stuck in `PENDING` state (no tasks enqueued)
- Manual workflow creation works but nothing executes
- Alert: custom `WorkflowLeaderStuck` alert firing

## Possible Causes
1. Leader pod crashed and standby pods also failed to detect it
2. K8s Lease API misconfigured (lease namespace/identity mismatch)
3. Network partition prevents leader from renewing lease
4. Clock skew between pods causes lease to expire prematurely
5. Bug in leader election algorithm (race condition)

## Diagnosis

```bash
# Check which pod is currently the leader
kubectl logs -n syncflow -l app=syncflow --tail=200 | grep -i leader

# Check K8s lease object
kubectl get lease -n syncflow syncflow-workflow-lease -o yaml

# Check pod status
kubectl get pods -n syncflow -l app=syncflow -o wide

# Check clock sync across pods
for pod in $(kubectl get pods -n syncflow -l app=syncflow -o name); do
  echo "=== $pod ==="
  kubectl exec -n syncflow $pod -- date
done

# Check workflow scheduler state
curl -u admin:$TOKEN http://syncflow.example.com/api/admin/tenants | jq .

# Check active workflows
curl -u admin:$TOKEN http://syncflow.example.com/api/workflows | jq '.[] | {id, status}'
```

## Recovery Steps

### Step 1: Identify the issue
```bash
# Which pods are alive?
kubectl get pods -n syncflow -l app=syncflow

# Check if leader pod is in the list
kubectl get pods -n syncflow -l app=syncflow -o name | head -5
```

### Step 2: For crashed leader pod
```bash
# K8s should auto-restart. Check status
kubectl get pods -n syncflow -l app=syncflow

# If auto-restart failed, manually delete the pod
kubectl delete pod -n syncflow -l app=syncflow,role=leader

# Watch standby pod take over
kubectl logs -n syncflow -l app=syncflow,role=standby --tail=20 -f
```

### Step 3: Force new leader election
```bash
# Delete the lease to force re-election
kubectl delete lease -n syncflow syncflow-workflow-lease

# Wait for election (usually < 5s)
sleep 10

# Verify new leader
kubectl get lease -n syncflow syncflow-workflow-lease -o yaml

# Check scheduler status
curl -u admin:$TOKEN http://syncflow.example.com/api/admin/tenants | jq .
```

### Step 4: For clock skew
```bash
# Sync clocks via NTP
for pod in $(kubectl get pods -n syncflow -l app=syncflow -o name); do
  kubectl exec -n syncflow $pod -- ntpdate -q time.google.com
done

# Or restart NTP on the host nodes
ssh node1 sudo systemctl restart systemd-timesyncd
```

### Step 5: Verify workflows resume
```bash
# Watch tick logs for 30s
kubectl logs -n syncflow -l app=syncflow --tail=100 -f | grep -i tick

# Confirm workflows progress
curl -u admin:$TOKEN http://syncflow.example.com/api/workflows | jq '.[] | {id, status}'
```

## Escalation
- If leader election does not recover within 5 minutes: page **Platform Lead** and **SRE**
- For repeated election failures: open P1 incident, consider pausing all pipelines
- For clock skew issues: engage infrastructure team (NTP, time sync)

## Post-Incident
- [ ] Add alert for `WorkflowScheduler.isLeader() == false` sustained > 60s
- [ ] Add monitoring for K8s lease object existence and age
- [ ] Implement automatic leader election health check
- [ ] Add chaos test: kill leader pod, verify re-election within 60s
- [ ] Document any K8s version-specific issues found
