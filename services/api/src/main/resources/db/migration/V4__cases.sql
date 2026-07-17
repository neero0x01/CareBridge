-- Cases + per-tenant case_number counter (MUH-10).

CREATE TABLE tenant_case_counters (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants (id),
    next_value  BIGINT NOT NULL
);

CREATE TABLE cases (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants (id),
    case_number           VARCHAR(50) NOT NULL,
    title                 VARCHAR(500) NOT NULL,
    type                  VARCHAR(50) NOT NULL,
    priority              VARCHAR(50) NOT NULL,
    status                VARCHAR(50) NOT NULL,
    patient_display_name  VARCHAR(255) NOT NULL,
    patient_ref           VARCHAR(100) NOT NULL,
    description           TEXT,
    created_by            UUID NOT NULL REFERENCES users (id),
    assignee_id           UUID REFERENCES users (id),
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cases_tenant_case_number UNIQUE (tenant_id, case_number),
    CONSTRAINT ck_cases_type CHECK (type IN (
        'REFERRAL', 'PRESCRIPTION_REVIEW', 'DISCHARGE', 'LAB_FOLLOWUP', 'OTHER'
    )),
    CONSTRAINT ck_cases_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_cases_status CHECK (status IN (
        'TO_DO', 'IN_REVIEW', 'NEEDS_INFO', 'APPROVED', 'REJECTED'
    ))
);

CREATE INDEX idx_cases_tenant_id ON cases (tenant_id);
CREATE INDEX idx_cases_tenant_status ON cases (tenant_id, status);
CREATE INDEX idx_cases_tenant_assignee ON cases (tenant_id, assignee_id);
CREATE INDEX idx_cases_tenant_priority ON cases (tenant_id, priority);
