package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgreTenantIamProvisioningInputSnapshotRepositoryAdapterTest {

    @Test
    void saveIfAbsent_shouldCastDesiredStatePayloadToJsonb() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        PostgreTenantIamProvisioningInputSnapshotRepositoryAdapter repository =
                new PostgreTenantIamProvisioningInputSnapshotRepositoryAdapter(jdbcTemplate);

        repository.saveIfAbsent(desiredState(), CorrelationId.of("corr-123"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(sqlCaptor.getValue()).contains("?::jsonb");
    }

    private static TenantIamDesiredState desiredState() {
        return TenantIamDesiredState.ofMinimalInput(
                TenantId.of("tenant-jsonb"),
                TenantName.of("tenant-jsonb"),
                Tier.of("standard"),
                Email.of("admin@example.com")
        );
    }
}
