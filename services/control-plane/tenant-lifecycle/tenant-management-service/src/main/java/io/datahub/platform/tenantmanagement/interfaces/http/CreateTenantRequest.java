package io.datahub.platform.tenantmanagement.interfaces.http;

import io.datahub.platform.tenantmanagement.domain.TenantTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateTenantRequest(
        @NotBlank String tenantName,
        @NotNull TenantTier tier,
        @NotBlank String region,
        String planConfig,
        Instant contractEndAt
) {
}
