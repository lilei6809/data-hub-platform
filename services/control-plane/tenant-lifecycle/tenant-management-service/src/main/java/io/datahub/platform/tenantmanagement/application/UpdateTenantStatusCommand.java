package io.datahub.platform.tenantmanagement.application;

import io.datahub.platform.tenantmanagement.domain.TenantStatus;

import java.util.UUID;

public record UpdateTenantStatusCommand(UUID tenantId, TenantStatus status) {
}
