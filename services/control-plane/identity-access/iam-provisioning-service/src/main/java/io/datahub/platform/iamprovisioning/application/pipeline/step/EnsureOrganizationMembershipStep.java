package io.datahub.platform.iamprovisioning.application.pipeline.step;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningCheckpoint;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakOperationException;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.OrganizationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
@Slf4j
public class EnsureOrganizationMembershipStep implements TenantIamProvisioningStep {
    private final static String NAME = "EnsureOrganizationMembershipStep";
    private final KeycloakAdminPort keycloakAdminPort;

    public EnsureOrganizationMembershipStep(KeycloakAdminPort keycloakAdminPort) {
        this.keycloakAdminPort = keycloakAdminPort;
    }

    @Override
    public StepExecutionContext ensure(TenantIamDesiredState desired, StepExecutionContext context) {

        try {

            OrganizationId organizationId = context.requireOrganizationId(name());
            UserId userId = context.requireUserId(name());

            keycloakAdminPort.ensureOrganizationMembership(organizationId, userId);

            return context;
        } catch (IamProvisioningException ex){

            //TODO: log

            throw ex;

        }
        catch (KeycloakOperationException ex) {

            //TODO: log

            throw new IamProvisioningException(
                    NAME,
                    ex.getFailureCode(),
                    ex.getMessage(),
                    ex.isRetryable(),
                    ex
            );
        }
    }

    @Override
    public TenantIamProvisioningCheckpoint checkpoint() {
        return TenantIamProvisioningCheckpoint.ORGANIZATION_MEMBERSHIP_CREATED;
    }

    @Override
    public String name() {
        return NAME;
    }
}
