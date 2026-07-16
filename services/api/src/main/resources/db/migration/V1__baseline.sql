-- CareBridge Flyway baseline.
-- Domain tables (tenants, users, cases, audit, webhooks, outbox) land in later milestones.
-- This migration only proves Flyway is wired and leaves a marker for M0 verification.

CREATE TABLE schema_baseline (
    id          BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO schema_baseline (id) VALUES (TRUE);
