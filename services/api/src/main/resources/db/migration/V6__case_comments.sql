-- Case comments (MUH-12).

CREATE TABLE case_comments (
    id          UUID PRIMARY KEY,
    case_id     UUID NOT NULL REFERENCES cases (id),
    tenant_id   UUID NOT NULL REFERENCES tenants (id),
    author_id   UUID NOT NULL REFERENCES users (id),
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_case_comments_case ON case_comments (tenant_id, case_id, created_at);
