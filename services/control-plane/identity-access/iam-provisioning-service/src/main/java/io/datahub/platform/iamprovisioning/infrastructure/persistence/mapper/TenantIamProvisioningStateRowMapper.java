package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningStateRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

// ResultSet -> TenantIamProvisioningStateRow    // JDBC 技术映射
public class TenantIamProvisioningStateRowMapper implements RowMapper<TenantIamProvisioningStateRow> {

    @Nullable
    @Override
    public TenantIamProvisioningStateRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TenantIamProvisioningStateRow(
                rs.getString("tenant_id"),
                rs.getString("workflow_correlation_id"),
                rs.getString("iam_status"),
                rs.getInt("retry_count"),
                rs.getLong("version"),
                rs.getBoolean("keycloak_organization_created"),
                rs.getBoolean("admin_user_created"),
                rs.getBoolean("default_roles_assigned"),
                rs.getBoolean("admin_user_membership_created"),
                rs.getTimestamp("last_attempt_at") == null ? null : rs.getTimestamp("last_attempt_at").toInstant(),
                rs.getTimestamp("provisioned_at") == null ? null : rs.getTimestamp("provisioned_at").toInstant(),
                rs.getTimestamp("failed_at") == null ? null : rs.getTimestamp("failed_at").toInstant(),
                rs.getTimestamp("next_retry_at") == null ? null : rs.getTimestamp("next_retry_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("claimed_by"),
                rs.getTimestamp("claimed_at") == null ? null : rs.getTimestamp("claimed_at").toInstant(),
                rs.getString("failure_message"));

    }
}
