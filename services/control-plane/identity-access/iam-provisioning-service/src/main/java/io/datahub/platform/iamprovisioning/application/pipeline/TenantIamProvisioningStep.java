package io.datahub.platform.iamprovisioning.application.pipeline;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningCheckpoint;

public interface TenantIamProvisioningStep {

    /**
     * 执行本步骤，将系统状态向 desiredState 靠拢。
     *
     * ensure 语义四条规则：
     * 1. 目标对象不存在 → 创建
     * 2. 目标对象已存在 → 复用（不报错，不重建）
     * 3. 关系/属性不一致 → 按 Desired State 校正
     * 4. 外部系统返回 409 Conflict → 查询已有对象后继续
     *
     * @param desired  期望状态（只读输入，描述"应该是什么样"）
     * @param context  执行上下文（前序步骤的输出，供后续步骤读取）
     * @return 更新后的 context（不可变风格，返回新实例）
     * @throws IamProvisioningException 当步骤确实无法完成时（不是 409 这种可恢复的情况）
     */
    StepExecutionContext ensure(TenantIamDesiredState desired, StepExecutionContext context);

    TenantIamProvisioningCheckpoint checkpoint();

    String name();

}
