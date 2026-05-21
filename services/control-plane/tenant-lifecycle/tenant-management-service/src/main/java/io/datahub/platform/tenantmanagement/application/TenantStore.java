package io.datahub.platform.tenantmanagement.application;

import io.datahub.platform.tenantmanagement.domain.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantStore {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID tenantId);
}
