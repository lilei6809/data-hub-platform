package io.datahub.platform.iamprovisioning.infrastructure.persistence;

// 纯数据容器 — 只是数据库行的 Java 映射，没有任何行为

import java.time.Instant;

public record TenantIamProvisioningStateRow(
        String tenantId,
        String workflowCorrelationId,
        String iamStatus,
        int retryCount,
        long version,

        boolean keycloakOrganizationCreated,
        boolean adminUserCreated,
        boolean defaultRolesAssigned,
        boolean adminUserMembershipCreated,

        Instant lastAttemptAt,
        Instant provisionedAt,
        Instant failedAt,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt,

        String failureMessage
) {
    



}
