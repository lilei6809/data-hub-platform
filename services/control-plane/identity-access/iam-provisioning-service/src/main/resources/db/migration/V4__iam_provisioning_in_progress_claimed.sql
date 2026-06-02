ALTER TABLE tenant_iam_provisioning_state
    ADD COLUMN claimed_by  VARCHAR(128),
    ADD COLUMN claimed_at  TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_iam_state_stale_in_progress
    ON tenant_iam_provisioning_state (claimed_at)
    WHERE iam_status = 'IAM_IN_PROGRESS';
