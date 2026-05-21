package io.datahub.platform.tenantmanagement.infrastructure;

import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import io.datahub.platform.tenantmanagement.domain.TenantTier;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("tenants")
public class TenantEntity {

    @Id
    private UUID tenantId;
    private String tenantName;
    private TenantTier tier;
    private TenantStatus status;
    private String region;
    private String planConfig;
    private Instant createdAt;
    private Instant contractEndAt;

    public TenantEntity() {
    }

    public TenantEntity(UUID tenantId, String tenantName, TenantTier tier, TenantStatus status,
                        String region, String planConfig, Instant createdAt, Instant contractEndAt) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.tier = tier;
        this.status = status;
        this.region = region;
        this.planConfig = planConfig;
        this.createdAt = createdAt;
        this.contractEndAt = contractEndAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public TenantTier getTier() {
        return tier;
    }

    public void setTier(TenantTier tier) {
        this.tier = tier;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPlanConfig() {
        return planConfig;
    }

    public void setPlanConfig(String planConfig) {
        this.planConfig = planConfig;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getContractEndAt() {
        return contractEndAt;
    }

    public void setContractEndAt(Instant contractEndAt) {
        this.contractEndAt = contractEndAt;
    }
}
