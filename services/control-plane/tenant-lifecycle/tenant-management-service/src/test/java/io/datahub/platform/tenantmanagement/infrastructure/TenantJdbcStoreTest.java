package io.datahub.platform.tenantmanagement.infrastructure;

import io.datahub.platform.tenantmanagement.domain.Tenant;
import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import io.datahub.platform.tenantmanagement.domain.TenantTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantJdbcStoreTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @InjectMocks
    private TenantJdbcStore tenantJdbcStore;

    @Test
    void saveInsertsTenantWhenIdDoesNotExist() {
        Tenant tenant = sampleTenant();
        when(tenantRepository.existsById(tenant.tenantId())).thenReturn(false);
        when(jdbcAggregateTemplate.insert(any(TenantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant savedTenant = tenantJdbcStore.save(tenant);

        verify(jdbcAggregateTemplate).insert(any(TenantEntity.class));
        verify(tenantRepository, never()).save(any(TenantEntity.class));
        assertThat(savedTenant.tenantId()).isEqualTo(tenant.tenantId());
    }

    @Test
    void saveUpdatesTenantWhenIdAlreadyExists() {
        Tenant tenant = sampleTenant();
        when(tenantRepository.existsById(tenant.tenantId())).thenReturn(true);
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant savedTenant = tenantJdbcStore.save(tenant);

        verify(tenantRepository).save(any(TenantEntity.class));
        verify(jdbcAggregateTemplate, never()).insert(any(TenantEntity.class));
        assertThat(savedTenant.status()).isEqualTo(tenant.status());
    }

    private Tenant sampleTenant() {
        return new Tenant(
                UUID.fromString("2eaa9247-5fa8-4f9b-8365-c009f3cffe6f"),
                "Acme",
                TenantTier.GROWTH,
                TenantStatus.PROVISIONING,
                "ap-southeast-1",
                "{\"max_datasources\":5}",
                Instant.parse("2026-05-16T00:00:00Z"),
                null
        );
    }
}
