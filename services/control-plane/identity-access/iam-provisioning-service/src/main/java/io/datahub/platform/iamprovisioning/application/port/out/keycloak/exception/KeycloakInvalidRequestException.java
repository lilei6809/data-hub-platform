package io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

// 参数非法：不可重试（同样的参数重试也没用）
// 场景：400（参数校验失败）、其他 4xx（非 409 Conflict、非 404 请求的资源不存在）
// 语义：我们发送的请求有问题，重试不会解决
public class KeycloakInvalidRequestException extends KeycloakOperationException {
    public KeycloakInvalidRequestException(TenantId tenantId, String operation, int httpStatus, Throwable cause) {
        super(tenantId,  operation, IamProvisioningFailureCode.KEYCLOAK_CLIENT_ERROR,
                "Keycloak rejected request during " + operation + " (HTTP " + httpStatus + ")", cause, false);
    }
}
