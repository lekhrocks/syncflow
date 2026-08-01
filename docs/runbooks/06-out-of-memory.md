# Runbook: Out of Memory (OOM)

> **Severity:** SEV2 — Pod restarts, degraded throughput  
> **Owner:** Platform Engineering

## Symptoms
- HighMemory alert firing (heap usage > 90% for 5m)
- Pod restart with OOMKilled status
- JVM metrics show heap growing continuously without GC recovery
- Error logs: `java.lang.OutOfMemoryError: Java heap space`
- Kubernetes reports OOMKilled for container
- Slow response times across APIs prior to crash

## Possible Causes
1. Unbounded in-memory data structures (event queue, checkpoint store, DLQ)
2. Memory leak in plugin connector
3. Too many concurrent snapshot batches loading results into memory
4. CDC event queue growing faster than sync consumer can drain
5. Inefficient query returning large result sets (> 1M rows)
6. Heap too small for workload (limit below actual requirement)

## Diagnosis

```bash
# Check pod OOM status
kubectl describe pod -n syncflow -l app=syncflow | grep -A 5 "State:\|Last State:"
# Look for "OOMKilled: true"

# Check JVM heap metrics via Prometheus
# Query: jvm_memory_used_bytes{area="heap"}
# Query: jvm_memory_max_bytes{area="heap"}

# Check queue sizes
curl -u admin:$TOKEN http://syncflow.example.com/api/admin/metrics | jq '{queueDepth: .queue, dlqSize: .dlq}'

# Check GC activity
kubectl exec -n syncflow deploy/syncflow -- jcmd 1 GC.heap_info 2>/dev/null || true

# Analyze heap dump (if available)
kubectl cp syncflow-pod:/tmp/heapdump.hprof ./heapdump.hprof
jhat ./heapdump.hprof  # or use Eclipse MAT
```

## Recovery Steps

### Step 1: Immediate (restore service)
```bash
# Increase memory limits
kubectl set resources deployment syncflow -n syncflow --limits memory=4Gi --requests memory=1Gi

# Or scale horizontally to distribute load
kubectl scale deployment syncflow -n syncflow --replicas=5
```

### Step 2: Drain accumulated backlogs
```bash
# Check DLQ for accumulated events
curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq 'length'

# Replay or clear DLQ if needed
for id in $(curl -u admin:$TOKEN http://syncflow.example.com/api/dlq | jq -r '.[].id'); do
  curl -X DELETE -u admin:$TOKEN http://syncflow.example.com/api/dlq/$id
done

# Restart after OOM
kubectl rollout restart deployment syncflow -n syncflow
```

### Step 3: Verify settings
```bash
# Check batch size (reduce if too large)
curl -u admin:$TOKEN http://syncflow.example.com/api/pipelines | jq '.[] | {name, batchSize}'

# Check queue capacity (currently 10,000 — reduce if source bursts)
# Update via Helm: --set queue.maxSize=5000
```

### Step 4: Enable GC logging (for future incidents)
```bash
# Add -XX:+PrintGCDetails -XX:+PrintGCDateStamps to JVM options
# Already configured: -XX:+UseZGC
# Verify: kubectl exec deploy/syncflow -n syncflow -- ps aux | grep java

# Check ZGC effectiveness
kubectl logs -n syncflow -l app=syncflow --tail=100 | grep -i "gc\|memory\|heap"
```

## Escalation
- OOM causes > 2 pod restarts in 1 hour: page Platform Lead
- DLQ grows > 10,000 entries: page Platform Lead
- OOM persists after memory increase: open SRE incident

## Post-Incident
- [ ] Right-size JVM heap limits based on peak usage
- [ ] Add memory leak detection in CI (JMH memory profiler)
- [ ] Add bounded data structure enforcement (CodeQL query)
- [ ] Review unbounded collection usage in codebase
- [ ] Add memory threshold alerts with burn rate
