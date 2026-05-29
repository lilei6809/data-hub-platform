package io.datahub.platform.iamprovisioning.application.pipeline;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.OrganizationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.UserId;
import lombok.Getter;

import java.util.Objects;
import java.util.Optional;

// 值对象（Value Object）：不可变，通过 with 方法创建新实例
// 注意：字段用 Optional 包装，表示"尚未填充"而非"null 的空指针陷阱"
@Getter
public final class StepExecutionContext {

    private final TenantId tenantId;
    private final CorrelationId correlationId;

    private final Optional<OrganizationId> organizationId;
    private final Optional<UserId> userId;

    // 私有构造函数，强制通过工厂方法创建
    private StepExecutionContext(TenantId tenantId, CorrelationId correlationId, Optional<OrganizationId> organizationId, Optional<UserId> userId) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.correlationId = Objects.requireNonNull(correlationId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.userId = Objects.requireNonNull(userId);
    }
    // 初始化时使用的工厂方法
    public static StepExecutionContext init(TenantId tenantId, CorrelationId correlationId) {
        return new StepExecutionContext(tenantId, correlationId,
                Optional.empty(), Optional.empty());
    }

    // 派生方法：Step 1 写入 organizationId 后，返回一个新的 context
    // 旧的 context 不变——这就是不可变性的含义
    // TODO: 配置写保护, 不允许对已有的 id 进行更新?
    public StepExecutionContext withOrganizationId(OrganizationId organizationId) {
        // 重复执行时，可以校验新值和旧值是否一致
        // 不一致说明出了严重的一致性问题
        return new StepExecutionContext(this.tenantId, this.correlationId,
                Optional.of(organizationId), this.userId);
    }

    public StepExecutionContext withUserId(UserId userId) {
        return new StepExecutionContext(
                this.tenantId,
                this.correlationId,
                this.organizationId,
                Optional.of(userId)
        );
    }

    public OrganizationId requireOrganizationId(String stepName) {
        return organizationId.orElseThrow(
                () -> IamProvisioningException.missingContextValue(
                        stepName,
                        "OrganizationId"
                )
        );
    }

    public UserId requireUserId(String stepName) {
        return userId.orElseThrow(
                () -> IamProvisioningException.missingContextValue(
                        stepName,
                        "UserId"
                )
        );
    }

}
