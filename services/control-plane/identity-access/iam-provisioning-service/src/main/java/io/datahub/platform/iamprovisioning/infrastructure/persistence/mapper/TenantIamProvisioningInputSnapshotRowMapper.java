package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshotRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TenantIamProvisioningInputSnapshotRowMapper implements RowMapper<TenantIamProvisioningInputSnapshotRow> {

    @Nullable
    @Override
    public TenantIamProvisioningInputSnapshotRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TenantIamProvisioningInputSnapshotRow(
                rs.getString("tenant_id"),
                rs.getString("workflow_correlation_id"),
                rs.getInt("schema_version"),
                rs.getString("desired_state_payload")
        );
    }
}
