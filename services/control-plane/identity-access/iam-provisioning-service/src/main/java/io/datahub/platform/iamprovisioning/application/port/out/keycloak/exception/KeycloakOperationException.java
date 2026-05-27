package io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import lombok.Getter;

// 基类 — 所有 Port 异常的根
// 注意：409 Conflict 不需要单独异常类型！
// ensure 语义要求 Adapter 内部消化 409，fallback 查询已有对象，
// 然后正常返回 ID，不向上抛异常。
@Getter
public class KeycloakOperationException extends RuntimeException {
    private final String operation;      // 哪个 Port 方法失败了，e.g. "ensureOrganization"
    private final TenantId tenantId;       // 哪个租户，便于日志关联
    private final IamProvisioningFailureCode failureCode;    // 机器可读的失败码，供状态机存储
    private final boolean retryable;     // Pipeline 的重试决策依赖这个字段

    protected KeycloakOperationException(TenantId tenantId, String operation, IamProvisioningFailureCode failureCode,
                                         String message, Throwable cause, boolean retryable) {
        // 保留 cause：翻译异常时绝对不能丢失原始堆栈
        // 否则排查线上问题会非常困难
        super(message, cause);
        this.tenantId = tenantId;
        this.operation = operation;
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

}
