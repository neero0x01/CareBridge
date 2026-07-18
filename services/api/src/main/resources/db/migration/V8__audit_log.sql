-- Append-only audit log for Case and User mutations (MUH-14).

CREATE TABLE audit_log (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants (id),
    actor_id     UUID REFERENCES users (id),
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    UUID NOT NULL,
    before_json  TEXT,
    after_json   TEXT,
    ip           VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_tenant_entity ON audit_log (tenant_id, entity_type, entity_id);
