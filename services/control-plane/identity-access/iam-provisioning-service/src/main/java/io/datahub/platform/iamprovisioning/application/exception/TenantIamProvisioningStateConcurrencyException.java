package io.datahub.platform.iamprovisioning.application.exception;

import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

public class TenantIamProvisioningStateConcurrencyException extends RuntimeException {

    public TenantIamProvisioningStateConcurrencyException(TenantId tenantId, long expectedVersion, long actualVersion) {
        super("TenantIamProvisioningState concurrent update conflict: tenantId=%s, expectedVersion=%d, actualVersion=%d"
                .formatted(tenantId, expectedVersion, actualVersion));
    }
}
