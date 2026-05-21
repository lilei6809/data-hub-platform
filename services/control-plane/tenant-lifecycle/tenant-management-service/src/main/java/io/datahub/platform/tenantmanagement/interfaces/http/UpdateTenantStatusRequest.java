package io.datahub.platform.tenantmanagement.interfaces.http;

import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantStatusRequest(@NotNull TenantStatus status) {
}
