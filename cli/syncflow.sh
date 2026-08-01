#!/usr/bin/env bash
set -euo pipefail

VERSION="0.1.0"
API_ENDPOINT="${SYNCFLOW_API:-http://localhost:8080}"
TOKEN_FILE="${HOME}/.syncflow/token"
CONFIG_FILE="${HOME}/.syncflow/config"

mkdir -p "${HOME}/.syncflow"

usage() {
    cat <<EOF
SyncFlow CLI v${VERSION}

Usage:
  syncflow login                          Authenticate with API server
  syncflow pipeline create <name>        Create a pipeline
  syncflow pipeline list                  List all pipelines
  syncflow pipeline deploy <id>          Deploy (validate + start) a pipeline
  syncflow agent register                 Register a managed agent
  syncflow agent list                     List registered agents
  syncflow workflow list                  List all workflows
  syncflow metrics                        Show platform metrics
  syncflow logs <pipeline-id>             Show logs for a pipeline
  syncflow status                         Show platform status
  syncflow help                           Show this message

Environment:
  SYNCFLOW_API        API endpoint (default: http://localhost:8080)
  SYNCFLOW_TOKEN      API token (alternative to login)
EOF
}

require_token() {
    if [ -n "${SYNCFLOW_TOKEN:-}" ]; then
        TOKEN="$SYNCFLOW_TOKEN"
    elif [ -f "$TOKEN_FILE" ]; then
        TOKEN=$(cat "$TOKEN_FILE")
    else
        echo "❌ Not authenticated. Run 'syncflow login' or set SYNCFLOW_TOKEN."
        exit 1
    fi
}

api() {
    local method="$1" path="$2" data="${3:-}"
    local args=(-s -X "$method" "$API_ENDPOINT$path" -H "Authorization: Bearer ${TOKEN:-}")
    if [ -n "$data" ]; then
        args+=(-H "Content-Type: application/json" -d "$data")
    fi
    curl "${args[@]}"
}

cmd_login() {
    read -rp "API Token: " token
    echo "$token" > "$TOKEN_FILE"
    chmod 600 "$TOKEN_FILE"
    echo "✅ Authenticated"
}

cmd_pipeline_create() {
    require_token
    local name="${1:-}"
    if [ -z "$name" ]; then
        read -rp "Pipeline name: " name
    fi
    read -rp "Source connection ID: " src
    read -rp "Source schema: " schema
    read -rp "Source table: " table
    read -rp "Destination connection ID: " dst
    read -rp "Destination schema: " d_schema
    read -rp "Destination table: " d_table
    local data
    data=$(cat <<JSON
{"name":"$name","sourceConnectionId":"$src","sourceSchema":"$schema","sourceTable":"$table","destConnectionId":"$dst","destSchema":"$d_schema","destTable":"$d_table"}
JSON
    )
    api POST "/api/pipelines" "$data" | jq '.'
}

cmd_pipeline_list() {
    require_token
    api GET "/api/pipelines" | jq '.[] | {id, name, status}'
}

cmd_pipeline_deploy() {
    require_token
    local id="${1:-}"
    [ -z "$id" ] && { echo "Usage: syncflow pipeline deploy <id>"; exit 1; }
    echo "Validating pipeline..."
    api POST "/api/pipelines/$id/validate" | jq '.'
    echo "Starting snapshot..."
    api POST "/api/pipelines/$id/snapshot" | jq '.status'
    echo "✅ Pipeline $id deployed"
}

cmd_agent_register() {
    require_token
    local hostname="${1:-$(hostname)}"
    local data="{\"version\":\"${VERSION}\",\"hostname\":\"${hostname}\",\"region\":\"local\",\"capabilities\":[\"SNAPSHOT\",\"CDC\",\"SYNCHRONIZATION\"],\"labels\":{},\"environment\":\"development\"}"
    api POST "/api/agents/register" "$data" | jq '{id: .id.value, status}'
    echo "✅ Agent registered"
}

cmd_agent_list() {
    require_token
    api GET "/api/agents" | jq '.[] | {id: .id.value, hostname, status, region, lastHeartbeat}'
}

cmd_workflow_list() {
    require_token
    api GET "/api/workflows" | jq '.[] | {id: .id.value, .pipelineId, status}'
}

cmd_metrics() {
    require_token
    api GET "/api/dashboard/metrics" | jq '.'
}

cmd_logs() {
    local pipeline_id="${1:-}"
    [ -z "$pipeline_id" ] && { echo "Usage: syncflow logs <pipeline-id>"; exit 1; }
    echo "Fetching logs for pipeline $pipeline_id..."
    echo "Run: kubectl logs -n syncflow -l app=syncflow --tail=100 | grep $pipeline_id"
}

cmd_status() {
    api GET "/api/health" | jq '{status, connectors: [.connectors[] | {type, status}]}'
}

case "${1:-help}" in
    login) cmd_login ;;
    pipeline)
        shift
        case "${1:-}" in
            create) cmd_pipeline_create "${2:-}" ;;
            list) cmd_pipeline_list ;;
            deploy) cmd_pipeline_deploy "${2:-}" ;;
            *) echo "Usage: syncflow pipeline {create|list|deploy}" ;;
        esac
        ;;
    agent)
        shift
        case "${1:-}" in
            register) cmd_agent_register "${2:-}" ;;
            list) cmd_agent_list ;;
            *) echo "Usage: syncflow agent {register|list}" ;;
        esac
        ;;
    workflow) shift; cmd_workflow_list ;;
    metrics) cmd_metrics ;;
    logs) cmd_logs "${2:-}" ;;
    status) cmd_status ;;
    help|*) usage ;;
esac
