package io.datahub.platform.iamprovisioning.infrastructure.keycloak.exception;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.KeycloakOperation;

// 场景：401 Unauthorized 未认证、403 Forbidden 已认证，但没权限
// 语义：Adapter 使用的 Service Account 凭证有问题，重试没有意义
// 这类错误需要立即告警，因为说明运维配置有问题
public class KeycloakAuthenticationException  extends KeycloakOperationException {

    public KeycloakAuthenticationException (
            TenantId  tenantId,
            KeycloakOperation operation,
            String message, Throwable cause) {
        super(tenantId, operation, IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED,
                "Keycloak authentication failed during " + operation, cause, false);
    }


}
