-- Transactional outbox for domain events (MUH-15). Worker publishes at-least-once.

CREATE TABLE outbox_messages (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload_json    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    attempts        INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_messages_unprocessed
    ON outbox_messages (created_at ASC)
    WHERE processed_at IS NULL;

CREATE INDEX idx_outbox_messages_tenant_created
    ON outbox_messages (tenant_id, created_at DESC);
