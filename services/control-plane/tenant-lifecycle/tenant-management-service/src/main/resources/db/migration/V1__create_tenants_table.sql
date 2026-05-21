CREATE TABLE IF NOT EXISTS tenants (
    tenant_id UUID PRIMARY KEY,
    tenant_name VARCHAR(255) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    region VARCHAR(128) NOT NULL,
    plan_config TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    contract_end_at TIMESTAMPTZ
);
