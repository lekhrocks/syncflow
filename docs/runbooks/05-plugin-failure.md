# Runbook: Plugin Failure

> **Severity:** SEV3 — Degraded functionality for affected connector  
> **Owner:** Platform Engineering  

## Symptoms
- `GET /api/agents` shows agent with status `ERROR`
- `GET /api/connections/test` returns failure for affected connector type
- Alert: `PluginError` firing
- User reports "Plugin not loaded" or "Connection test failed" errors

## Possible Causes
1. Plugin JAR incompatible with current platform version
2. Plugin has missing manifest fields (Plugin-Id, Plugin-Connector-Class)
3. Plugin class initialization throws (missing dependency, version conflict)
4. Plugin targets a connection type that is no longer supported
5. Plugin JAR corrupted during upload
6. Plugin revoked manually by admin

## Diagnosis

```bash
# List all installed plugins
curl -u admin:$TOKEN http://syncflow.example.com/api/plugins | jq '.[] | {id, lifecycle, status}'

# Check plugin status
curl -u admin:$TOKEN http://syncflow.example.com/api/plugins/{id} | jq .

# Test specific connection
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/connections/test \
  -d '{"host":"...","port":...,"database":"...","connectionType":"..."}'

# Check control plane logs for plugin errors
kubectl logs -n syncflow -l app=syncflow --tail=200 | grep -i plugin
```

## Recovery Steps

### Step 1: Identify the failure
```bash
# Check plugin lifecycle
curl -u admin:$TOKEN http://syncflow.example.com/api/plugins/{id} | jq '.lifecycle'

# If lifecycle is ERROR, look at recent error logs
kubectl logs -n syncflow -l app=syncflow --tail=200 | grep -A 3 "{plugin_id}"
```

### Step 2: Disable and remove
```bash
# Disable the plugin (stops usage but keeps installed)
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/plugins/{id}/disable

# Remove completely
curl -X DELETE -u admin:$TOKEN http://syncflow.example.com/api/plugins/{id}
```

### Step 3: Reinstall with correct version
```bash
# Verify plugin version compatibility
# Check platform version
curl -u admin:$TOKEN http://syncflow.example.com/api/admin/tenants

# Download correct plugin version (matching platform)
# Verify plugin manifest contains valid version
unzip -p plugin.jar META-INF/MANIFEST.MF | head -20

# Reinstall
curl -X POST -u admin:$TOKEN \
  http://syncflow.example.com/api/plugins/install \
  -F "file=@plugin-v1.2.0.jar"
```

### Step 4: Verify plugin loaded
```bash
# Check plugin lifecycle
curl -u admin:$TOKEN http://syncflow.example.com/api/plugins/{id} | jq '.lifecycle'
# Should be "INSTALLED" or "ENABLED"

# Test connection
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/connections/test \
  -d '{...}'

# Re-enable if needed
curl -X POST -u admin:$TOKEN http://syncflow.example.com/api/plugins/{id}/enable
```

## Escalation
- For third-party plugin issues: contact plugin vendor
- For platform version mismatch: open architecture review ticket
- For repeated plugin crashes: page Platform Lead

## Post-Incident
- [ ] Verify plugin version compatibility matrix
- [ ] Add plugin signature verification (planned feature)
- [ ] Add alert for plugin ERROR state sustained > 5 minutes
- [ ] Document the plugin's compatibility requirements
