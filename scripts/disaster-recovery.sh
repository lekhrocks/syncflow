#!/bin/bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-syncflow}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
REGION="${REGION:-us-east-1}"
S3_BUCKET="${S3_BUCKET:-syncflow-backups}"

echo "=== SyncFlow Disaster Recovery ==="
echo "Region: $REGIONS | Namespace: $NAMESPACE"

failover() {
    echo "Performing cross-region failover to $REGION..."
    kubectl config use-context "$REGION-eks"
    kubectl -n "$NAMESPACE" scale deploy/syncflow --replicas=3
    kubectl -n "$NAMESPACE" rollout status deploy/syncflow --timeout=120s
    echo "Failover complete. Verifying health..."
    kubectl -n "$NAMESPACE" get pods
}

backup() {
    local TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    echo "Starting backup: $TIMESTAMP"

    kubectl exec deploy/syncflow -n "$NAMESPACE" -- pg_dump -U syncflow syncflow > "$BACKUP_DIR/syncflow_$TIMESTAMP.sql"

    kubectl get configmap -n "$NAMESPACE" -o yaml > "$BACKUP_DIR/configmaps_$TIMESTAMP.yaml"
    kubectl get secret -n "$NAMESPACE" -o name > "$BACKUP_DIR/secrets_$TIMESTAMP.txt"

    if command -v aws &> /dev/null; then
        aws s3 cp "$BACKUP_DIR/syncflow_$TIMESTAMP.sql" "s3://$S3_BUCKET/$REGION/"
        echo "Backup uploaded to S3: s3://$S3_BUCKET/$REGION/"
    fi
}

restore() {
    local FILE="${1:-}"
    if [ -z "$FILE" ]; then
        echo "Usage: $0 restore <backup-file>"
        exit 1
    fi
    echo "Restoring from $FILE..."
    kubectl exec -i deploy/syncflow -n "$NAMESPACE" -- psql -U syncflow syncflow < "$FILE"
    echo "Restore complete"
}

case "${1:-}" in
    failover) failover ;;
    backup) backup ;;
    restore) restore "${2:-}" ;;
    *)
        echo "Usage: $0 {failover|backup|restore <file>}"
        exit 1
        ;;
esac
