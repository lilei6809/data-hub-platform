package io.datahub.platform.tenantmanagement.interfaces.http;

import io.datahub.platform.tenantmanagement.domain.Tenant;
import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import io.datahub.platform.tenantmanagement.domain.TenantTier;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID tenantId,
        String tenantName,
        TenantTier tier,
        TenantStatus status,
        String region,
        String planConfig,
        Instant createdAt,
        Instant contractEndAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.tenantId(),
                tenant.tenantName(),
                tenant.tier(),
                tenant.status(),
                tenant.region(),
                tenant.planConfig(),
                tenant.createdAt(),
                tenant.contractEndAt()
        );
    }
}
