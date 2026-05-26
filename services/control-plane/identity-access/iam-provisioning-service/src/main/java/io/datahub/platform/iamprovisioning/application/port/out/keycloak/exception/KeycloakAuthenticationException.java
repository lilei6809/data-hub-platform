package io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception;

import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

// 场景：401 Unauthorized 未认证、403 Forbidden 已认证，但没权限
// 语义：Adapter 使用的 Service Account 凭证有问题，重试没有意义
// 这类错误需要立即告警，因为说明运维配置有问题
public class KeycloakAuthenticationException  extends KeycloakOperationException {
    private static final String FAILURE_CODE = "KEYCLOAK_AUTH_FAILED";
    public KeycloakAuthenticationException (
            TenantId  tenantId,
            String operation,
            String message, Throwable cause) {
        super(tenantId, operation, FAILURE_CODE,
                "Keycloak authentication failed during " + operation, cause, false);
    }
}
