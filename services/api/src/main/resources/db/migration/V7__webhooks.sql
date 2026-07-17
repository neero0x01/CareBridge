-- Per-tenant webhook secret (AES-GCM ciphertext) + inbound event idempotency (MUH-13).

ALTER TABLE tenants
    ADD COLUMN webhook_secret_ciphertext TEXT NOT NULL DEFAULT '';

-- Existing rows (if any) must not keep an empty ciphertext; seed/demo will set secrets explicitly.
-- For greenfield installs the default is replaced at Tenant register.

CREATE TABLE webhook_events (
    id                 UUID NOT NULL,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    type               VARCHAR(100) NOT NULL,
    payload_json       TEXT NOT NULL,
    signature_valid    BOOLEAN NOT NULL,
    processed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_webhook_events_tenant_processed ON webhook_events (tenant_id, processed_at);
