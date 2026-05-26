package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;

import java.util.Objects;

public record OrganizationAttributes(TenantId tenantId,
                                     TenantName displayName,
                                     Tier tier) {

    public OrganizationAttributes {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(tier, "tier is required");
        Objects.requireNonNull(displayName, "displayName is required");
    }

    public static OrganizationAttributes from(TenantIamDesiredState desiredState) {
        Objects.requireNonNull(desiredState, "tenantIamDesiredState is required");
        return new OrganizationAttributes(
                desiredState.tenantId(),
                desiredState.tenantName(),
                desiredState.tier()
        );
    }
}
