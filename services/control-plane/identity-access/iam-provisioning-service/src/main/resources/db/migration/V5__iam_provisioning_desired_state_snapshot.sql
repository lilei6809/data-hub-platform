CREATE TABLE tenant_iam_provisioning_input_snapshot (
                                                        tenant_id VARCHAR(128) PRIMARY KEY,
                                                        workflow_correlation_id VARCHAR(128) NOT NULL,
                                                        schema_version INTEGER NOT NULL,
                                                        desired_state_payload JSONB NOT NULL,
                                                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
