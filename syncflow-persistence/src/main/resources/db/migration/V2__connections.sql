CREATE TABLE connections (
    id                  VARCHAR(36) PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    connection_type     VARCHAR(20) NOT NULL,
    host                VARCHAR(255) NOT NULL,
    port                INTEGER NOT NULL,
    database            VARCHAR(255) NOT NULL,
    options_            TEXT,
    encrypted_username  VARCHAR(1024) NOT NULL,
    encrypted_password  VARCHAR(1024) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    db_version          VARCHAR(50),
    driver_name         VARCHAR(50),
    last_latency_ms     BIGINT DEFAULT 0,
    last_checked        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_connections_type ON connections(connection_type);
CREATE INDEX idx_connections_status ON connections(status);
