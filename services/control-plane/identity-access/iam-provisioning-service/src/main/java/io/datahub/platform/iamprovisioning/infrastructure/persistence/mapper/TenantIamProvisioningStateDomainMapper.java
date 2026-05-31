package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.TenantIamProvisioningStateRow;
import org.springframework.stereotype.Component;

// Mapper，负责在 Domain Model 和 Row 之间双向转换
public class TenantIamProvisioningStateDomainMapper {

    public TenantIamProvisioningState toDomain(TenantIamProvisioningStateRow row){

        TenantIamProvisioningState state = new TenantIamProvisioningState(
                TenantId.of(row.tenantId()),
                CorrelationId.of(row.workflowCorrelationId()),
                row.createdAt()
        );

        state.restoreFrom(row);
        return state;
    }

    public TenantIamProvisioningStateRow toRow(TenantIamProvisioningState domain){

        return new TenantIamProvisioningStateRow(
                domain.getTenantId().value(),
                domain.getWorkflowCorrelationId().value(),
                domain.getOverallStatus().name(),
                domain.getRetryCount(),
                domain.getVersion(),
                domain.isKeycloakOrganizationCreated(),
                domain.isAdminUserCreated(),
                domain.isDefaultRolesAssigned(),
                domain.isAdminUserMembershipCreated(),
                domain.getLastAttemptAt(),
                domain.getProvisionedAt(),
                domain.getFailedAt(),
                domain.getNextRetryAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                domain.getFailureMessage()
        );
    }
}
