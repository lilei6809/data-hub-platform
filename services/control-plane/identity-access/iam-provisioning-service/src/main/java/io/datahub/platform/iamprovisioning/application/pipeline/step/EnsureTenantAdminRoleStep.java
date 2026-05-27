package io.datahub.platform.iamprovisioning.application.pipeline.step;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningCheckpoint;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakOperationException;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.RealmRoleName;
import io.datahub.platform.iamprovisioning.domain.valueobject.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;


@Component
@Order(3)
@Slf4j
public class EnsureTenantAdminRoleStep implements TenantIamProvisioningStep {
    private final static String NAME = "EnsureTenantAdminRoleStep";
    private static final RealmRoleName TENANT_ADMIN = RealmRoleName.of("TENANT_ADMIN");


    private final KeycloakAdminPort keycloakAdminPort;

    public EnsureTenantAdminRoleStep(KeycloakAdminPort keycloakAdminPort) {
        this.keycloakAdminPort = keycloakAdminPort;
    }

    @Override
    public StepExecutionContext ensure(TenantIamDesiredState desired, StepExecutionContext context) {

        try {

            UserId userId = context.requireUserId(name());

            keycloakAdminPort.ensureUserRealmRole(userId, TENANT_ADMIN);


            log.info("EnsureTenantAdminRoleStep Completed:  tenantId={}, organizationId={}, correlationId={}, userId={}: TENANT_ADMIN realm role Attached",
                    desired.tenantId(), context.getOrganizationId(), context.getCorrelationId(),userId);
            return context;
        } catch (IamProvisioningException ex){

            // 失败日志最好在 pipeline/application service 统一打一次，带 stepName /
            //  tenantId / correlationId / failureCode / retryable，避免重复堆栈。

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
        return TenantIamProvisioningCheckpoint.TENANT_ADMIN_ROLE_ASSIGNED;
    }

    @Override
    public String name() {
        return NAME;
    }
}
