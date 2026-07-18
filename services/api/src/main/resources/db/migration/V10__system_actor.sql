-- System Actor: non-login User per Tenant for lab webhook Case writes (C1).

ALTER TABLE users
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one System Actor per Tenant.
CREATE UNIQUE INDEX uq_users_one_system_per_tenant
    ON users (tenant_id)
    WHERE is_system = TRUE;

-- Backfill: one System Actor for each Tenant that lacks one.
-- password_hash is a bcrypt of a random unusable value (login rejects is_system first).
INSERT INTO users (
    id,
    tenant_id,
    email,
    password_hash,
    full_name,
    role,
    active,
    must_change_password,
    is_system,
    created_at
)
SELECT
    gen_random_uuid(),
    t.id,
    'system@carebridge.internal',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System',
    'AUDITOR',
    TRUE,
    FALSE,
    TRUE,
    NOW()
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.tenant_id = t.id AND u.is_system = TRUE
);
