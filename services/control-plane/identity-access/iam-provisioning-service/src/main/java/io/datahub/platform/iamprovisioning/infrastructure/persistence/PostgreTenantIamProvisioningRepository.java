package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.exception.ProvisioningStateNotFoundException;
import io.datahub.platform.iamprovisioning.application.exception.TenantIamProvisioningStateConcurrencyException;
import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningStateDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.TenantIamProvisioningStateRowMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningStateRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
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
                            updated_at = ?,
                            claimed_by = ?,
                            claimed_at = ?
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
                row.claimedBy(),
                toTimestamp(row.claimedAt()),

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

        // DB version 已递增，回写内存对象，保证后续 save 的 WHERE version = ? 能匹配
        tenantIamProvisioningState.markPersisted(row.version() + 1);

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

    @Transactional
    @Override
    public List<TenantIamProvisioningState> claimBatchReadyForRetry(int limit, String claimedBy, Instant timestamp) {

        // step1: 确认一批待重试的记录
        List<TenantIamProvisioningStateRow> rows = jdbcTemplate.query(
                """
                        SELECT * FROM tenant_iam_provisioning_state
                        WHERE iam_status = 'IAM_AWAITING_RETRY' AND next_retry_at <= now()
                        ORDER BY next_retry_at ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """, rowMapper, limit);

        List<String> ids = rows.stream()
                .map(TenantIamProvisioningStateRow::tenantId)
                .toList();

        // step2: 认领当前批记录, 状态 -> IN_PROGRESS
        for (String id : ids) {
            jdbcTemplate.update("""
                        UPDATE tenant_iam_provisioning_state
                        SET iam_status = 'IAM_IN_PROGRESS',
                            last_attempt_at = ?,
                            updated_at = ?,
                            next_retry_at = NULL,
                            claimed_at = ?,
                            claimed_by = ?,
                            version = version + 1
                        WHERE tenant_id = ? AND iam_status = 'IAM_AWAITING_RETRY'
            """, toTimestamp(timestamp), toTimestamp(timestamp), toTimestamp(timestamp), claimedBy, id);
        }

        // rows 没更新, 我重新查一遍, 拿新的结果
        rows.clear();

        for (String id : ids) {
            TenantIamProvisioningStateRow row = jdbcTemplate.queryForObject(
                    """
                            SELECT * FROM tenant_iam_provisioning_state
                            WHERE tenant_id = ?
                            """, rowMapper, id);
            rows.add(row);
        }


        return rows.stream().map(domainMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void claim(String tenantId, String claimedBy, Instant timestamp) {
        jdbcTemplate.update(
                """
                    UPDATE tenant_iam_provisioning_state
                    SET iam_status = 'IAM_IN_PROGRESS', claimed_at = ?, claimed_by = ?
                    WHERE tenant_id = ?
                    """,  Timestamp.from(timestamp), claimedBy, tenantId);

    }

    @Override
    public int reclaimStale(Duration staleThreshold) {
        int affected = jdbcTemplate.update(
                """
                        UPDATE tenant_iam_provisioning_state
                        SET iam_status = 'IAM_AWAITING_RETRY', claimed_at = NULL, claimed_by = NULL
                        WHERE iam_status = 'IAM_IN_PROGRESS' AND claimed_by IS NOT NULL 
                            AND claimed_at < now() - ? * INTERVAL '1 seconds'
                        """, staleThreshold.toSeconds());

        return affected;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
