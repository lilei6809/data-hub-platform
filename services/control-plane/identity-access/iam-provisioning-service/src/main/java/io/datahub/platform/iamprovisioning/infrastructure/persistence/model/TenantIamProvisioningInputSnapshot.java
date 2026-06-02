package io.datahub.platform.iamprovisioning.infrastructure.persistence.model;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

public record TenantIamProvisioningInputSnapshot(
        TenantId tenantId,
        CorrelationId correlationId,
        int schemaVersion,
        TenantIamDesiredState desiredState

) {

    public static TenantIamProvisioningInputSnapshot of(TenantId tenantId,
                                                        CorrelationId correlationId,
                                                        int schemaVersion,
                                                        TenantIamDesiredState desiredState){
        return new TenantIamProvisioningInputSnapshot(
                tenantId,
                correlationId,
                schemaVersion,
                desiredState
        );
    }
}
