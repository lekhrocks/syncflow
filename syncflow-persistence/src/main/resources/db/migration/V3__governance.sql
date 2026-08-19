CREATE TABLE IF NOT EXISTS schema_versions (
    id              VARCHAR(64) PRIMARY KEY,
    connection_id   VARCHAR(36) NOT NULL,
    schema          VARCHAR(255) NOT NULL,
    table_name      VARCHAR(255) NOT NULL,
    columns_json    JSONB NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,
    change_summary  TEXT,
    ddl_statement   TEXT,
    detected_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_schema_versions_conn_table ON schema_versions(connection_id, table_name);

CREATE TABLE IF NOT EXISTS data_lineage (
    id                      VARCHAR(64) PRIMARY KEY,
    pipeline_id             VARCHAR(36) NOT NULL,
    source_connection_id    VARCHAR(36) NOT NULL,
    source_schema           VARCHAR(255),
    source_table            VARCHAR(255),
    source_columns          TEXT,
    dest_connection_id      VARCHAR(36) NOT NULL,
    dest_schema             VARCHAR(255),
    dest_table              VARCHAR(255),
    dest_columns            TEXT,
    transformation_summary  TEXT,
    rows_processed          BIGINT DEFAULT 0,
    timestamp               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_data_lineage_pipeline ON data_lineage(pipeline_id);

CREATE TABLE IF NOT EXISTS column_tags (
    id              VARCHAR(64) PRIMARY KEY,
    connection_id   VARCHAR(36) NOT NULL,
    schema          VARCHAR(255) NOT NULL,
    table_name      VARCHAR(255) NOT NULL,
    column_name     VARCHAR(255) NOT NULL,
    tag             VARCHAR(50) NOT NULL,
    classification  VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(connection_id, schema, table_name, column_name)
);

CREATE INDEX idx_column_tags_classification ON column_tags(classification);
