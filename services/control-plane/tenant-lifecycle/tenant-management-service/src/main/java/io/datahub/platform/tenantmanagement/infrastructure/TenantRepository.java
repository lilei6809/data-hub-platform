package io.datahub.platform.tenantmanagement.infrastructure;

import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface TenantRepository extends CrudRepository<TenantEntity, UUID> {
}
