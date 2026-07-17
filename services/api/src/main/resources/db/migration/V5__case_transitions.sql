-- Case transition history (MUH-11).

CREATE TABLE case_transitions (
    id           UUID PRIMARY KEY,
    case_id      UUID NOT NULL REFERENCES cases (id),
    tenant_id    UUID NOT NULL REFERENCES tenants (id),
    from_status  VARCHAR(50) NOT NULL,
    to_status    VARCHAR(50) NOT NULL,
    actor_id     UUID NOT NULL REFERENCES users (id),
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_case_transitions_from CHECK (from_status IN (
        'TO_DO', 'IN_REVIEW', 'NEEDS_INFO', 'APPROVED', 'REJECTED'
    )),
    CONSTRAINT ck_case_transitions_to CHECK (to_status IN (
        'TO_DO', 'IN_REVIEW', 'NEEDS_INFO', 'APPROVED', 'REJECTED'
    ))
);

CREATE INDEX idx_case_transitions_case ON case_transitions (tenant_id, case_id, created_at);
