-- Application user accounts for JWT authentication and RBAC.
-- Named app_users to avoid colliding with sample/integration tables named "users".
CREATE TABLE IF NOT EXISTS app_users (
    id            VARCHAR(36) PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt (~60 chars)
    email         VARCHAR(255),
    roles         VARCHAR(512) DEFAULT 'USER',  -- CSV of role names
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Default admin account (password: admin-test-password — for dev/test; replace in prod).
-- PolicyResolver grants the 'admin' username full permissions.
INSERT INTO app_users (id, username, password_hash, email, roles, enabled)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    '$2a$10$kcqbSa6/YwMoZge2NPc5b.ASDIr7vXvAjZ6Amvfdl.A6z.azwH1Au',  -- BCrypt('admin-test-password')
    'admin@syncflow.local',
    'ADMIN',
    TRUE
)
ON CONFLICT (username) DO NOTHING;