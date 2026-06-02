package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningInputSnapshotRepository;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantIamProvisioningInputSnapshotRepositoryAdapter implements TenantIamProvisioningInputSnapshotRepository {

    private final ConcurrentHashMap<TenantId, TenantIamProvisioningInputSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public void saveIfAbsent(TenantIamDesiredState desired, CorrelationId correlationId) {
        Objects.requireNonNull(desired, "desired must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        TenantIamProvisioningInputSnapshot snapshot = TenantIamProvisioningInputSnapshot.of(
                desired.tenantId(),
                correlationId,
                1,
                desired
        );

        store.putIfAbsent(desired.tenantId(), snapshot);
    }

    @Override
    public Optional<TenantIamProvisioningInputSnapshot> findByTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Optional.ofNullable(store.get(tenantId));
    }
}
