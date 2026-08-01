#!/bin/bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-syncflow}"

echo "=== SyncFlow Self-Healing Check ==="

check_pods() {
    local unhealthy=0
    for pod in $(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=syncflow -o name); do
        local status=$(kubectl get "$pod" -n "$NAMESPACE" -o jsonpath='{.status.phase}')
        if [ "$status" != "Running" ]; then
            echo "Unhealthy pod: $pod (status: $status)"
            kubectl delete "$pod" -n "$NAMESPACE" --grace-period=30
            unhealthy=$((unhealthy + 1))
        fi
    done
    return $unhealthy
}

restart_crashed() {
    local crashed=$(kubectl get pods -n "$NAMESPACE" --field-selector=status.phase=Failed -o name | wc -l)
    if [ "$crashed" -gt 0 ]; then
        echo "Found $crashed crashed pods. Deleting..."
        kubectl delete pods -n "$NAMESPACE" --field-selector=status.phase=Failed
    fi
}

check_health() {
    local endpoint="${1:-http://localhost:8080/actuator/health}"
    if curl -sf "$endpoint" > /dev/null 2>&1; then
        echo "Health check passed: $endpoint"
        return 0
    else
        echo "Health check FAILED: $endpoint"
        return 1
    fi
}

echo "Checking pod health..."
check_pods || true

echo "Checking for crashed pods..."
restart_crashed

echo "Checking API health..."
check_health "http://localhost:8080/actuator/health"

echo "Self-healing check complete."
