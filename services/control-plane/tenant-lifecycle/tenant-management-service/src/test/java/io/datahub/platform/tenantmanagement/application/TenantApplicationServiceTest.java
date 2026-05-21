package io.datahub.platform.tenantmanagement.application;

import io.datahub.platform.tenantmanagement.domain.Tenant;
import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import io.datahub.platform.tenantmanagement.domain.TenantTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceTest {

    @Mock
    private TenantStore tenantStore;

    @Mock
    private TenantEventPublisher tenantEventPublisher;

    @InjectMocks
    private TenantApplicationService tenantApplicationService;

    @Captor
    private ArgumentCaptor<Tenant> tenantCaptor;

    @Test
    void createTenantUsesProvisioningAsInitialStatus() {
        when(tenantStore.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant createdTenant = tenantApplicationService.createTenant(new CreateTenantCommand(
                "Acme",
                TenantTier.GROWTH,
                "ap-southeast-1",
                "{\"max_datasources\":5}",
                Instant.parse("2027-05-16T00:00:00Z")
        ));

        verify(tenantStore).save(tenantCaptor.capture());
        verify(tenantEventPublisher).publishTenantCreated(createdTenant);

        assertThat(createdTenant.status()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(tenantCaptor.getValue().tenantName()).isEqualTo("Acme");
        assertThat(createdTenant.tenantId()).isNotNull();
    }

    @Test
    void updateTenantStatusFailsWhenTenantDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        when(tenantStore.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantApplicationService.updateTenantStatus(
                new UpdateTenantStatusCommand(tenantId, TenantStatus.ACTIVE)
        )).isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining(tenantId.toString());
    }
}
