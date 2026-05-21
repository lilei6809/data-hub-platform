package io.datahub.platform.tenantmanagement.interfaces.http;

import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import io.datahub.platform.tenantmanagement.domain.TenantTier;

import java.util.UUID;

public record TenantContextResponse(
        UUID tenantId,
        TenantTier tier,
        TenantStatus status,
        String region,
        String planConfig
) {
}
