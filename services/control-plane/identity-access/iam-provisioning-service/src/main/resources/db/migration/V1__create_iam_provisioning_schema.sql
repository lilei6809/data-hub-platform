CREATE TABLE tenant_iam_provisioning_state (
    tenant_id VARCHAR(128) PRIMARY KEY,
    iam_status VARCHAR(64) NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    provisioned_at TIMESTAMPTZ,
    failure_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    workflow_correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
