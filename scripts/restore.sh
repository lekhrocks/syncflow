#!/bin/bash
set -euo pipefail

BACKUP_ID="${1:-}"
NAMESPACE="${NAMESPACE:-syncflow}"

if [ -z "$BACKUP_ID" ]; then
  echo "Usage: $0 <backup-timestamp>"
  echo "Available backups:"
  ls ./backups/
  exit 1
fi

BACKUP_DIR="./backups/$BACKUP_ID"
if [ ! -d "$BACKUP_DIR" ]; then
  echo "Backup not found: $BACKUP_DIR"
  exit 1
fi

echo "=== SyncFlow Restore: $BACKUP_ID ==="

# Restore database
if [ -f "$BACKUP_DIR/syncflow_db.sql" ]; then
  echo "Restoring database..."
  DB_POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=syncflow -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
  if [ -n "$DB_POD" ]; then
    kubectl exec -i -n "$NAMESPACE" "$DB_POD" -- psql -U syncflow syncflow < "$BACKUP_DIR/syncflow_db.sql" || \
      echo "WARNING: Database restore skipped"
  fi
fi

# Restore ConfigMaps
if [ -f "$BACKUP_DIR/configmaps.yaml" ]; then
  echo "Restoring ConfigMaps..."
  kubectl apply -f "$BACKUP_DIR/configmaps.yaml" -n "$NAMESPACE" || true
fi

echo "Restore initiated. Verify application health:"
echo "kubectl get pods -n $NAMESPACE"
echo "kubectl exec -n $NAMESPACE deploy/syncflow -- curl -s http://localhost:8080/actuator/health"
