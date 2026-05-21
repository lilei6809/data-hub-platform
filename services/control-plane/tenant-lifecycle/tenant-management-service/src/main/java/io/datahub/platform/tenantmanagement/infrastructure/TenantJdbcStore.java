package io.datahub.platform.tenantmanagement.infrastructure;

import io.datahub.platform.tenantmanagement.application.TenantStore;
import io.datahub.platform.tenantmanagement.domain.Tenant;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TenantJdbcStore implements TenantStore {

    private final TenantRepository tenantRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public TenantJdbcStore(TenantRepository tenantRepository, JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.tenantRepository = tenantRepository;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity tenantEntity = toEntity(tenant);
        TenantEntity savedEntity = tenantRepository.existsById(tenant.tenantId())
                ? tenantRepository.save(tenantEntity)
                : jdbcAggregateTemplate.insert(tenantEntity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Tenant> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(this::toDomain);
    }

    private TenantEntity toEntity(Tenant tenant) {
        return new TenantEntity(
                tenant.tenantId(),
                tenant.tenantName(),
                tenant.tier(),
                tenant.status(),
                tenant.region(),
                tenant.planConfig(),
                tenant.createdAt(),
                tenant.contractEndAt()
        );
    }

    private Tenant toDomain(TenantEntity entity) {
        return new Tenant(
                entity.getTenantId(),
                entity.getTenantName(),
                entity.getTier(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getPlanConfig(),
                entity.getCreatedAt(),
                entity.getContractEndAt()
        );
    }
}
