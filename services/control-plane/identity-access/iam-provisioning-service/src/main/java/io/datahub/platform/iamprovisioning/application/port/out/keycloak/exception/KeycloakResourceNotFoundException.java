package io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.KeycloakOperation;

// 场景：404（查询一个本应存在的资源时找不到）
// 语义：资源被外部删除，属于异常的状态漂移，视情况可以重试
// 注意：EnsureOrganizationStep 正常查询返回空不算异常，这里指 ensure 内部依赖资源消失
public class KeycloakResourceNotFoundException extends KeycloakOperationException {
    public KeycloakResourceNotFoundException(KeycloakOperation operation, TenantId tenantId,
                                             String resourceType, Throwable cause) {
        super(tenantId, operation, IamProvisioningFailureCode.KEYCLOAK_RESOURCE_NOT_FOUND,
                "Expected " + resourceType + " not found during " + operation, cause, true);
    }
}
