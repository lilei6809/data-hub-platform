package io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception;

import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

// 场景：5xx、网络超时、连接失败
// 语义：Keycloak 暂时不可用，值得重试
// 上游服务超时	504 Gateway Timeout
// 上游服务不可达	502 Bad Gateway
// 连接被拒绝	502 Bad Gateway
// 服务过载/熔断 503 Service Unavailable
public class KeycloakTransientException extends KeycloakOperationException {
    private static final String FAILURE_CODE = "KEYCLOAK_UNAVAILABLE";
    public KeycloakTransientException(String operation, TenantId tenantId, Throwable cause) {
        super(tenantId, operation, FAILURE_CODE,
                "Keycloak transient failure during " + operation, cause, true);
    }
}
