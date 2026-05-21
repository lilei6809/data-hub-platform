package io.datahub.platform.tenantmanagement.application;

import io.datahub.platform.tenantmanagement.domain.TenantTier;

import java.time.Instant;

public record CreateTenantCommand(
        String tenantName,
        TenantTier tier,
        String region,
        String planConfig,
        Instant contractEndAt
) {
}
