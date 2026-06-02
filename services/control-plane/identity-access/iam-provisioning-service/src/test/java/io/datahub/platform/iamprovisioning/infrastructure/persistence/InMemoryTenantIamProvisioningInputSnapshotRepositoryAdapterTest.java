package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTenantIamProvisioningInputSnapshotRepositoryAdapterTest {

    private final InMemoryTenantIamProvisioningInputSnapshotRepositoryAdapter repository =
            new InMemoryTenantIamProvisioningInputSnapshotRepositoryAdapter();

    @Test
    void findByTenantId_shouldReturnEmpty_whenSnapshotDoesNotExist() {
        assertThat(repository.findByTenantId(TenantId.of("tenant-missing"))).isEmpty();
    }

    @Test
    void saveIfAbsent_shouldPersistInitialSnapshot() {
        TenantIamDesiredState desired = desiredState("tenant-a", "admin-a@example.com");
        CorrelationId correlationId = CorrelationId.newCorrelationId();

        repository.saveIfAbsent(desired, correlationId);

        Optional<TenantIamProvisioningInputSnapshot> snapshot =
                repository.findByTenantId(TenantId.of("tenant-a"));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().tenantId()).isEqualTo(TenantId.of("tenant-a"));
        assertThat(snapshot.orElseThrow().correlationId()).isEqualTo(correlationId);
        assertThat(snapshot.orElseThrow().schemaVersion()).isEqualTo(1);
        assertThat(snapshot.orElseThrow().desiredState()).isEqualTo(desired);
    }

    @Test
    void saveIfAbsent_shouldKeepOriginalSnapshot_whenTenantAlreadyExists() {
        TenantIamDesiredState originalDesired = desiredState("tenant-a", "admin-a@example.com");
        CorrelationId originalCorrelationId = CorrelationId.newCorrelationId();
        TenantIamDesiredState laterDesired = desiredState("tenant-a", "admin-b@example.com");

        repository.saveIfAbsent(originalDesired, originalCorrelationId);
        repository.saveIfAbsent(laterDesired, CorrelationId.newCorrelationId());

        TenantIamProvisioningInputSnapshot snapshot =
                repository.findByTenantId(TenantId.of("tenant-a")).orElseThrow();

        assertThat(snapshot.correlationId()).isEqualTo(originalCorrelationId);
        assertThat(snapshot.desiredState()).isEqualTo(originalDesired);
    }

    private static TenantIamDesiredState desiredState(String tenantId, String adminEmail) {
        return TenantIamDesiredState.ofMinimalInput(
                TenantId.of(tenantId),
                TenantName.of(tenantId),
                Tier.of("standard"),
                Email.of(adminEmail)
        );
    }
}
