package io.datahub.platform.tenantmanagement.domain;

import java.time.Instant;
import java.util.UUID;

public record Tenant(
        UUID tenantId,
        String tenantName,
        TenantTier tier,
        TenantStatus status,
        String region,
        String planConfig,
        Instant createdAt,
        Instant contractEndAt
) {

    public Tenant withStatus(TenantStatus updatedStatus) {
        return new Tenant(
                tenantId,
                tenantName,
                tier,
                updatedStatus,
                region,
                planConfig,
                createdAt,
                contractEndAt
        );
    }
}
