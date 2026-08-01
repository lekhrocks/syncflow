# SyncFlow Terraform Provider
# Example: terraform apply -var="api_token=..." -var="api_endpoint=https://syncflow.example.com"

terraform {
  required_providers {
    syncflow = {
      source  = "syncflow/syncflow"
      version = "~> 0.1"
    }
  }
}

provider "syncflow" {
  endpoint = var.api_endpoint
  token    = var.api_token
}

# ──────────────────────────────────────────────
# Connections
# ──────────────────────────────────────────────

resource "syncflow_connection" "source_pg" {
  name            = "production-postgres"
  connection_type = "POSTGRESQL"
  host            = var.pg_host
  port            = var.pg_port
  database        = var.pg_database
  username        = var.pg_username
  password        = var.pg_password
  options = {
    sslmode = "require"
  }
}

resource "syncflow_connection" "dest_mongo" {
  name            = "analytics-mongodb"
  connection_type = "MONGODB"
  host            = var.mongo_host
  port            = 27017
  database        = var.mongo_database
  username        = var.mongo_username
  password        = var.mongo_password
}

# ──────────────────────────────────────────────
# Pipelines
# ──────────────────────────────────────────────

resource "syncflow_pipeline" "pg_to_mongo" {
  name                   = "pg-users-to-mongo"
  source_connection_id   = syncflow_connection.source_pg.id
  source_schema          = "public"
  source_table           = "users"
  dest_connection_id     = syncflow_connection.dest_mongo.id
  dest_schema            = "admin"
  dest_table             = "users"
  batch_size             = 5000
  sync_mode              = "CDC_SNAPSHOT_AND_INCREMENTAL"

  column_mapping {
    source      = "id"
    destination = "_id"
  }
  column_mapping {
    source      = "email"
    destination = "email"
    transform {
      type = "lowercase"
    }
  }
  column_mapping {
    source      = "full_name"
    destination = "name"
    transform {
      type = "rename"
      params = { newName = "name" }
    }
  }
}

# ──────────────────────────────────────────────
# Outputs
# ──────────────────────────────────────────────

output "pipeline_id" {
  value = syncflow_pipeline.pg_to_mongo.id
}

output "connection_ids" {
  value = {
    source      = syncflow_connection.source_pg.id
    destination = syncflow_connection.dest_mongo.id
  }
}
