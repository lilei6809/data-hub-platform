package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningInputSnapshotRepository;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningInputSnapshotDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningInputSnapshotRowMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshot;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshotRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Repository //TODO: 临时加上, 需要内存实现
public class PostgreTenantIamProvisioningInputSnapshotRepositoryAdapter implements TenantIamProvisioningInputSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    private final TenantIamProvisioningInputSnapshotDomainMapper domainMapper;
    private final TenantIamProvisioningInputSnapshotRowMapper rowMapper;

    public PostgreTenantIamProvisioningInputSnapshotRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.domainMapper = new TenantIamProvisioningInputSnapshotDomainMapper();
        this.rowMapper = new TenantIamProvisioningInputSnapshotRowMapper();
    }


    @Override
    public void saveIfAbsent(TenantIamDesiredState desired, CorrelationId correlationId) {
        TenantIamProvisioningInputSnapshot snapshot = TenantIamProvisioningInputSnapshot.of(
                desired.tenantId(),
                correlationId,
                1,
                desired
        );

        TenantIamProvisioningInputSnapshotRow row = domainMapper.toRow(snapshot);

        Instant now = Instant.now();

        int affected = jdbcTemplate.update(
                """
                        INSERT INTO tenant_iam_provisioning_input_snapshot(TENANT_ID, 
                                                                           WORKFLOW_CORRELATION_ID, 
                                                                           SCHEMA_VERSION, 
                                                                           DESIRED_STATE_PAYLOAD, 
                                                                           CREATED_AT, 
                                                                           UPDATED_AT)
                        VALUES (?, ?, ?, ?::jsonb, ?, ?)
                        ON CONFLICT DO NOTHING;
                        """, row.tenantId(), row.correlationId(), row.schemaVersion(),
                row.desiredState(),
                Timestamp.from(now),
                Timestamp.from(now)
        );


    }

    @Override
    public Optional<TenantIamProvisioningInputSnapshot> findByTenantId(TenantId tenantId) {
        TenantIamProvisioningInputSnapshotRow row = jdbcTemplate.queryForObject(
                """
                        SELECT * FROM tenant_iam_provisioning_input_snapshot
                        WHERE tenant_id = ?
                        """, rowMapper, tenantId);

        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(domainMapper.toDomain(row));
    }
}
