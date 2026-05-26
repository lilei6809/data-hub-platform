package io.datahub.platform.iamprovisioning.domain.model;

import io.datahub.platform.iamprovisioning.domain.exception.InvalidIamProvisioningStateTransitionException;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Getter
public class TenantIamProvisioningState {

    private final TenantId tenantId;

    private static final int MAX_RETRY = 5;  // 如果不是 static: 你有多少个租户，就会有多少个 TenantIamProvisioningState 实例，每一个都持有一个自己的 MAX_RETRY = 5，这既浪费内存，又在语义上表达了错误的含义（好像每个租户可以有不同的最大重试次数）。

    // 宏观状态机
    private IamProvisioningStatus overallStatus;

    // 子目标达成状态（Desired State 的核心）
    private boolean keycloakOrganizationCreated;
    private boolean defaultRolesAssigned;
    private boolean adminUserCreated;

    // 重试治理
    private int retryCount;
    private Instant lastAttemptAt;
    private Instant provisionedAt;              // 可变，仅在 PROVISIONED 时设置
    private IamProvisioningFailureCode  provisioningFailureCode;
    private String failureMessage;  // 便于运维排查

    // 审计字段
    private Instant createdAt;
    private Instant updatedAt;

    // 分布式追踪
    private CorrelationId workflowCorrelationId;

    public TenantIamProvisioningState(TenantId tenantId, CorrelationId correlationId, Instant now) {
        this.tenantId = Objects.requireNonNull(tenantId);

        this.workflowCorrelationId = Objects.requireNonNull(correlationId);

        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
        this.overallStatus = IamProvisioningStatus.PENDING;
        this.retryCount = 0;
    }


    public static TenantIamProvisioningState init(TenantId tenantId, CorrelationId correlationId, Instant now) {
        return new TenantIamProvisioningState(tenantId, correlationId, now);
    }


    // Application Service 调用这个方法，但它不知道内部如何校验
    // 它只是表达意图："我希望开始执行了"
    public void markInProgress(Instant now){
        // 校验规则住在领域对象里：PENDING 和 AWAITING_RETRY 才能转 IN_PROGRESS
        // 这保证了任何调用方都无法绕过这条规则
        if (overallStatus != IamProvisioningStatus.PENDING
        //&& overallStatus != IamProvisioningStatus.FAILED  //  FAILED 是终态，必须人工介入，不允许自动重新进入, 人工介入手动操作状态转移
                && overallStatus != IamProvisioningStatus.AWAITING_RETRY
        ) {
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.IN_PROGRESS,
                    "");
        }

        this.overallStatus = IamProvisioningStatus.IN_PROGRESS;
        this.lastAttemptAt = now;
        this.updatedAt = now;

    }

    public void markCompleted(Instant now){
        if ((!(keycloakOrganizationCreated && defaultRolesAssigned && adminUserCreated) )
        || (overallStatus != IamProvisioningStatus.IN_PROGRESS)) {
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.COMPLETED,
                    "Not all critical steps were completed successfully or Current IamProvisioningStatus is not IN_PROGRESS");
        }

        this.overallStatus = IamProvisioningStatus.COMPLETED;

        this.retryCount = 0;
        this.updatedAt = now;
        this.provisionedAt = now;

        // 成功时清除失败记录，由领域对象自己保证这个清理动作不会被遗忘
        this.provisioningFailureCode = null;
        this.failureMessage = null;

    }

    public boolean isRetryExhausted(){
        return retryCount >= MAX_RETRY;
    }

    // 当前执行尝试失败，并且不再自动重试
    public void markFailed(Instant now, IamProvisioningFailureCode code, String message){

        // markFailed 表达"这次执行失败，并且不再自动重试"。
        // 因此它必须发生在一次正在执行的尝试中，而不是等待重试或已完成之后。
        if (this.overallStatus != IamProvisioningStatus.IN_PROGRESS) {
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.FAILED, "");
        }

        this.retryCount++;
        this.lastAttemptAt = now;
        this.updatedAt = now;

        this.overallStatus = IamProvisioningStatus.FAILED;
        this.provisioningFailureCode = code;
        this.failureMessage = message;
    }

    // “当前尝试失败，但可重试”，会记录失败详情；达到最大重试次数后直接进入 FAILED
    public void markAwaitRetry(Instant now, IamProvisioningFailureCode code, String message){
        if (this.overallStatus != IamProvisioningStatus.IN_PROGRESS){
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.AWAITING_RETRY, "");
        }

        this.retryCount++;
        this.lastAttemptAt = now;
        this.updatedAt = now;
        this.provisioningFailureCode = code;
        this.failureMessage = message;

        if (isRetryExhausted()) {
            this.overallStatus = IamProvisioningStatus.FAILED;
        } else {
            this.overallStatus = IamProvisioningStatus.AWAITING_RETRY;
        }
    }

    // 每个子目标完成时，都有明确的领域方法来记录
    public void markOrganizationCreated(Instant now) {
        this.keycloakOrganizationCreated = true;
        this.updatedAt = now; // 或者由 Repository 管理
    }

    public void markDefaultRolesAssigned(Instant now) {
        this.defaultRolesAssigned = true;
        this.updatedAt = now;
    }

    public void markAdminUserCreated(Instant now) {
        this.adminUserCreated = true;
        this.updatedAt = now;
    }

    // Step Pipeline 通过这些只读方法决定是否跳过该步骤
//    public boolean isOrganizationCreated() {
//        return keycloakOrganizationCreated;
//    }

    @Override
    public String toString() {
        return "TenantIamProvisioningState{tenantId=%s, status=%s, retryCount=%d, failureCode=%s, correlationId=%s}"
                .formatted(tenantId, overallStatus, retryCount, provisioningFailureCode, workflowCorrelationId);
    }

}


