package io.datahub.platform.iamprovisioning.application.pipeline.step;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningCheckpoint;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakOperationException;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.OrganizationAttributes;
import io.datahub.platform.iamprovisioning.domain.valueobject.OrganizationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@Slf4j
public class EnsureOrganizationStep implements TenantIamProvisioningStep {

    private final static String NAME = "EnsureOrganizationStep";
    private final KeycloakAdminPort  keycloakAdminPort;

    public EnsureOrganizationStep(KeycloakAdminPort keycloakAdminPort) {
        this.keycloakAdminPort = keycloakAdminPort;
    }

    @Override
    public StepExecutionContext ensure(TenantIamDesiredState desired, StepExecutionContext context) {
        try {
            TenantId tenantId = desired.tenantId();
            OrganizationAttributes attributes = OrganizationAttributes.from(desired);

            OrganizationId organizationId = keycloakAdminPort.ensureOrganization(tenantId, attributes);
            log.info("EnsureOrganizationStep completed. tenantId={}, organizationId={}, correlationId={}",
                    tenantId, organizationId, context.getCorrelationId());
            return context.withOrganizationId(organizationId);
        }
        // 注意: 捕获 keycloak 异常, 翻译为业务 application service 异常向上抛
        catch (KeycloakOperationException ex) {

            //失败日志最好在 pipeline/application service 统一打一次，带 stepName /
            //  tenantId / correlationId / failureCode / retryable，避免重复堆栈。

            // 当前 layer 的异常翻译是必要的
            // 因为 adapter 层翻译的是 Keycloak SDK / HTTP / 409 / 404 / 5xx
            //    -> KeycloakOperationException
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
        return TenantIamProvisioningCheckpoint.ORGANIZATION_CREATED;
    }

    @Override
    public String name() {
        return NAME;
    }


}
