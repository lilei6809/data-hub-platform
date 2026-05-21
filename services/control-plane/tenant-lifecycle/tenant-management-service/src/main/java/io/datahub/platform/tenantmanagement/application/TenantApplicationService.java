package io.datahub.platform.tenantmanagement.application;

import io.datahub.platform.tenantmanagement.domain.Tenant;
import io.datahub.platform.tenantmanagement.domain.TenantContext;
import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TenantApplicationService {

    private final TenantStore tenantStore;
    private final TenantEventPublisher tenantEventPublisher;

    public TenantApplicationService(TenantStore tenantStore, TenantEventPublisher tenantEventPublisher) {
        this.tenantStore = tenantStore;
        this.tenantEventPublisher = tenantEventPublisher;
    }

    @Transactional
    public Tenant createTenant(CreateTenantCommand command) {
        Tenant tenant = new Tenant(
                UUID.randomUUID(),
                command.tenantName(),
                command.tier(),
                TenantStatus.PROVISIONING,
                command.region(),
                command.planConfig(),
                Instant.now(),
                command.contractEndAt()
        );

        Tenant savedTenant = tenantStore.save(tenant);
        tenantEventPublisher.publishTenantCreated(savedTenant);
        return savedTenant;
    }

    @Transactional(readOnly = true)
    public Tenant getTenant(UUID tenantId) {
        return tenantStore.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Transactional
    public Tenant updateTenantStatus(UpdateTenantStatusCommand command) {
        Tenant tenant = getTenant(command.tenantId());
        Tenant updatedTenant = tenant.withStatus(command.status());
        Tenant savedTenant = tenantStore.save(updatedTenant);
        tenantEventPublisher.publishTenantStatusChanged(savedTenant);
        return savedTenant;
    }

    @Transactional(readOnly = true)
    public TenantContext getTenantContext(UUID tenantId) {
        Tenant tenant = getTenant(tenantId);
        return new TenantContext(
                tenant.tenantId(),
                tenant.tier(),
                tenant.status(),
                tenant.region(),
                tenant.planConfig()
        );
    }
}
