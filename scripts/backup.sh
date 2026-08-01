#!/bin/bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-syncflow}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DB_POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=syncflow -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

echo "=== SyncFlow Backup: $TIMESTAMP ==="
mkdir -p "$BACKUP_DIR/$TIMESTAMP"

# Database backup
echo "Backing up PostgreSQL..."
kubectl exec -n "$NAMESPACE" "$DB_POD" -- pg_dump -U syncflow syncflow > "$BACKUP_DIR/$TIMESTAMP/syncflow_db.sql" 2>/dev/null || \
  echo "WARNING: Database backup skipped (no direct DB pod access)"

# ConfigMaps backup
echo "Backing up ConfigMaps..."
kubectl get configmap -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/$TIMESTAMP/configmaps.yaml" 2>/dev/null

# Secrets backup (metadata only - values are encrypted)
echo "Backing up Secrets (metadata)..."
kubectl get secret -n "$NAMESPACE" -o name > "$BACKUP_DIR/$TIMESTAMP/secrets.txt" 2>/dev/null

# Pipeline export via API
echo "Backing up pipeline definitions..."
POD=$(kubectl get pod -n "$NAMESPACE" -l app.kubernetes.io/name=syncflow -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -n "$POD" ]; then
  kubectl exec -n "$NAMESPACE" "$POD" -- curl -s http://localhost:8080/api/pipelines > "$BACKUP_DIR/$TIMESTAMP/pipelines.json" 2>/dev/null || true
fi

echo "Backup complete: $BACKUP_DIR/$TIMESTAMP"
echo "To restore, run: ./scripts/restore.sh $TIMESTAMP"
