package io.datahub.platform.iamprovisioning.application.pipeline.step;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningCheckpoint;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakOperationException;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.AdminUser;
import io.datahub.platform.iamprovisioning.domain.valueobject.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@Slf4j
public class EnsureAdminUserStep implements TenantIamProvisioningStep {

    private final static String NAME = "EnsureAdminUserStep";
    private final KeycloakAdminPort keycloakAdminPort;

    public EnsureAdminUserStep(KeycloakAdminPort keycloakAdminPort) {
        this.keycloakAdminPort = keycloakAdminPort;
    }

    @Override
    public StepExecutionContext ensure(TenantIamDesiredState desired, StepExecutionContext context) {

        try {
            AdminUser adminUser = desired.adminUser();

            UserId userId = keycloakAdminPort.ensureUser(
                    context.getTenantId(),
                    adminUser.email(),
                    adminUser.temporaryCredentialPolicy()
            );
            log.info("EnsureAdminUserStep completed. userId={}, correlationId={}",
                    userId, context.getCorrelationId());
            return context.withUserId(userId);
        } catch (KeycloakOperationException ex){
            //  step: 翻译 keycloak 异常 -> 业务异常
            throw new IamProvisioningException(
                    name(),
                    ex.getFailureCode(),
                    ex.getMessage(),
                    ex.isRetryable(),
                    ex
            );
        }
    }

    @Override
    public TenantIamProvisioningCheckpoint checkpoint() {
        return TenantIamProvisioningCheckpoint.ADMIN_USER_CREATED;
    }

    @Override
    public String name() {
        return NAME;
    }
}
