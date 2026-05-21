package io.datahub.platform.tenantmanagement.domain;

import java.util.UUID;

public record TenantContext(
        UUID tenantId,
        TenantTier tier,
        TenantStatus status,
        String region,
        String planConfig
) {
}
