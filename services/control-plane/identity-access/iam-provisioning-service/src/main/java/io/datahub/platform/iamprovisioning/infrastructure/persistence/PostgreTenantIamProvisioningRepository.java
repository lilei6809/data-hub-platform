package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.exception.ProvisioningStateNotFoundException;
import io.datahub.platform.iamprovisioning.application.exception.TenantIamProvisioningStateConcurrencyException;
import io.datahub.platform.iamprovisioning.application.port.out.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningStateDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningStateRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PostgreTenantIamProvisioningRepository implements TenantIamProvisioningStateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TenantIamProvisioningStateDomainMapper domainMapper =  new TenantIamProvisioningStateDomainMapper();
    private final TenantIamProvisioningStateRowMapper rowMapper =  new TenantIamProvisioningStateRowMapper();

    public PostgreTenantIamProvisioningRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TenantIamProvisioningState findOrInitById(TenantId tenantId, CorrelationId correlationId) {
        // Step 1: 尝试插入（原子操作，无并发风险）
        jdbcTemplate.update(
                """
                    INSERT INTO tenant_iam_provisioning_state(
                      tenant_id, iam_status, retry_count, version,
                                                              workflow_correlation_id, created_at, updated_at
                    ) 
                    VALUES (
                            ?, 'IAM_PENDING', 0, 0, ?, now(), now()
                    ) 
                    ON CONFLICT (tenant_id) DO NOTHING
                    """,
                tenantId.value(), correlationId.value()
        );

        // Step 2: 无论是新插入还是已存在，都读取当前状态
        TenantIamProvisioningStateRow row = jdbcTemplate.queryForObject(
                """
                        SELECT * FROM tenant_iam_provisioning_state
                        WHERE tenant_id = ?
                        """, rowMapper, tenantId.value()
        );

        return domainMapper.toDomain(row);
    }

    @Override
    public void save(TenantIamProvisioningState tenantIamProvisioningState) {
        TenantIamProvisioningStateRow row = domainMapper.toRow(tenantIamProvisioningState);

        int affected = 0;

        affected = jdbcTemplate.update(
                """
                    UPDATE tenant_iam_provisioning_state
                        SET iam_status = ?,
                            retry_count = ?,
                            version = ?,
                            keycloak_organization_created = ?,
                            admin_user_created = ?,
                            default_roles_assigned = ?,
                            admin_user_membership_created = ?,
                            last_attempt_at = ?,
                            provisioned_at = ?,
                            failed_at = ?,
                            next_retry_at = ?,
                            failure_message = ?,
                            workflow_correlation_id = ?,
                            updated_at = ?
                        WHERE tenant_id = ?
                          AND version = ?
                    """,
                row.iamStatus(),
                row.retryCount(),
                row.version() + 1,
                row.keycloakOrganizationCreated(),
                row.adminUserCreated(),
                row.defaultRolesAssigned(),
                row.adminUserMembershipCreated(),
                toTimestamp(row.lastAttemptAt()),
                toTimestamp(row.provisionedAt()),
                toTimestamp(row.failedAt()),
                toTimestamp(row.nextRetryAt()),
                row.failureMessage(),
                row.workflowCorrelationId(),
                toTimestamp(row.updatedAt()),
                row.tenantId(),
                row.version()
            );

        if (affected == 0){
            // affected=0 有两种可能：
            // 1. 有人在我之前改了 version（并发冲突）
            // 2. 这条记录根本不存在（异常的外部删除）
            // 区分方法：再查一次
            Optional<TenantIamProvisioningState> existing = findByTenantId(tenantIamProvisioningState.getTenantId());

            if (existing.isPresent()){
                throw new TenantIamProvisioningStateConcurrencyException(
                        tenantIamProvisioningState.getTenantId(),
                        row.version(),
                        existing.get().getVersion()
                );
            } else {
                throw new ProvisioningStateNotFoundException(tenantIamProvisioningState.getTenantId());
            }
        }

    }

    @Transactional  // 事务边界：锁在 state 更新为 IN_PROGRESS 后返回
    @Override
    public List<TenantIamProvisioningState> findReadyForRetry(Instant now, int limit) {
        List<TenantIamProvisioningStateRow> retries = jdbcTemplate.query(
                """
                        SELECT * FROM tenant_iam_provisioning_state
                        WHERE iam_status = 'IAM_AWAITING_RETRY'
                        AND next_retry_at <= ?
                        ORDER BY next_retry_at ASC 
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED 
                        """,
                rowMapper,
                Timestamp.from(now),
                limit);

        return retries.stream().map(domainMapper::toDomain).toList();
    }

    @Override
    public Optional<TenantIamProvisioningState> findByTenantId(TenantId tenantId) {
        return jdbcTemplate.query(
                        """
                                SELECT * FROM tenant_iam_provisioning_state
                                WHERE tenant_id = ?
                                """,
                        rowMapper,
                        tenantId.value()
                )
                .stream()
                .findFirst()
                .map(domainMapper::toDomain);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
