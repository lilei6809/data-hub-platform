package io.datahub.platform.iamprovisioning.application.port.out.repository;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshot;

import java.util.Optional;

public interface TenantIamProvisioningInputSnapshotRepository {

    void saveIfAbsent(TenantIamDesiredState desired, CorrelationId correlationId);

    Optional<TenantIamProvisioningInputSnapshot> findByTenantId(TenantId tenantId);
}
