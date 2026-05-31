package io.datahub.platform.iamprovisioning.domain.model;

import io.datahub.platform.iamprovisioning.domain.exception.InvalidIamProvisioningStateTransitionException;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.TenantIamProvisioningStateRow;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Slf4j
@Getter
public class TenantIamProvisioningState {

    private final TenantId tenantId;

    private static final int MAX_RETRY = 5;  // 如果不是 static: 你有多少个租户，就会有多少个 TenantIamProvisioningState 实例，每一个都持有一个自己的 MAX_RETRY = 5，这既浪费内存，又在语义上表达了错误的含义（好像每个租户可以有不同的最大重试次数）。
    private Instant nextRetryAt;  // 下次允许重试的时间点，null 表示不需要等待

    // 宏观状态机
    private IamProvisioningStatus overallStatus;

    // 子目标达成状态（Desired State 的核心）
    private boolean keycloakOrganizationCreated;
    private boolean defaultRolesAssigned;
    private boolean adminUserCreated;
    private boolean adminUserMembershipCreated;

    // 重试治理
    private int retryCount;
    private Instant lastAttemptAt;
    private Instant provisionedAt;              // 可变，仅在 PROVISIONED 时设置
    private Instant failedAt;
    private IamProvisioningFailureCode  provisioningFailureCode;
    private String failureMessage;  // 便于运维排查

    // 审计字段
    private Instant createdAt;
    private Instant updatedAt;

    private long version;

    // 分布式追踪
    private CorrelationId workflowCorrelationId;

    public TenantIamProvisioningState(TenantId tenantId, CorrelationId correlationId, Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId);

        this.workflowCorrelationId = Objects.requireNonNull(correlationId);

        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
        this.overallStatus = IamProvisioningStatus.IAM_PENDING;
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
        if (overallStatus != IamProvisioningStatus.IAM_PENDING
        //&& overallStatus != IamProvisioningStatus.FAILED  //  FAILED 是终态，必须人工介入，不允许自动重新进入, 人工介入手动操作状态转移
                && overallStatus != IamProvisioningStatus.IAM_AWAITING_RETRY
        ) {
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.IAM_IN_PROGRESS,
                    "");
        }

        this.overallStatus = IamProvisioningStatus.IAM_IN_PROGRESS;
        this.lastAttemptAt = now;
        this.updatedAt = now;

    }

    public void markCompleted(Instant now){
        if ((!(keycloakOrganizationCreated && defaultRolesAssigned && adminUserCreated && adminUserMembershipCreated) )
        || (overallStatus != IamProvisioningStatus.IAM_IN_PROGRESS)) {
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.IAM_COMPLETED,
                    "Not all critical steps were completed successfully or Current IamProvisioningStatus is not IN_PROGRESS");
        }

        this.overallStatus = IamProvisioningStatus.IAM_COMPLETED;

        this.retryCount = 0;
        this.updatedAt = now;
        this.provisionedAt = now;

        // 成功时清除失败记录，由领域对象自己保证这个清理动作不会被遗忘
        this.provisioningFailureCode = null;
        this.failureMessage = null;
        this.nextRetryAt = null;

    }

    public boolean isRetryExhausted(){
        return retryCount >= MAX_RETRY;
    }

    // 当前执行尝试失败，并且不再自动重试
    public void markFailed(Instant now, IamProvisioningFailureCode code, String message){

        // markFailed 表达"这次执行失败，并且不再自动重试"。
        // 因此它必须发生在一次正在执行的尝试中，而不是等待重试或已完成之后。
        if (this.overallStatus != IamProvisioningStatus.IAM_IN_PROGRESS) {
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.IAM_FAILED, "");
        }

        this.retryCount++;
        this.lastAttemptAt = now;
        this.updatedAt = now;
        this.failedAt = now;

        this.overallStatus = IamProvisioningStatus.IAM_FAILED;
        this.provisioningFailureCode = code;
        this.failureMessage = message;
    }

    // “当前尝试失败，但可重试”，会记录失败详情；达到最大重试次数后直接进入 FAILED
    public void markAwaitRetry(Instant markTime, IamProvisioningFailureCode code, String message){
        if (this.overallStatus != IamProvisioningStatus.IAM_IN_PROGRESS){
            throw new InvalidIamProvisioningStateTransitionException(overallStatus, IamProvisioningStatus.IAM_AWAITING_RETRY, "");
        }

        this.retryCount++;
        this.lastAttemptAt = markTime;
        this.updatedAt = markTime;
        this.provisioningFailureCode = code;
        this.failureMessage = message;

        if (isRetryExhausted()) {
            this.overallStatus = IamProvisioningStatus.IAM_FAILED;
        } else {
            this.overallStatus = IamProvisioningStatus.IAM_AWAITING_RETRY;

            // TODO: retryScheduler.scheduleRetry(tenantId, state.nextRetryAt());
            this.nextRetryAt = markTime.plus(nextRetryDelay(), ChronoUnit.SECONDS);
        }
    }

    private Long nextRetryDelay() {
        // 基础退避：1min, 2min, 4min, 8min, 16min...
        long baseDelaySeconds = (long) Math.pow(2, retryCount) * 60;

        // 加上随机抖动：±30% 的随机偏移
        long jitterSeconds = (long) (baseDelaySeconds * 0.3 * Math.random());

        // 设置上限，最长不超过 30 分钟
        long totalSeconds = Math.min(baseDelaySeconds + jitterSeconds, 1800);

        return totalSeconds;
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

    public void markAdminUserMembershipCreated(Instant now) {
         this.adminUserMembershipCreated = true;
         this.updatedAt = now;
    }

    public void markPersisted(long version){
        this.version = version;
    }

    public void markStepCompleted(TenantIamProvisioningCheckpoint checkpoint, Instant now){
        switch (checkpoint){
            case ORGANIZATION_CREATED:
                markOrganizationCreated(now);
                break;
            case ADMIN_USER_CREATED:
                markAdminUserCreated(now);
                break;
            case TENANT_ADMIN_ROLE_ASSIGNED:
                markDefaultRolesAssigned(now);
                break;
            case ORGANIZATION_MEMBERSHIP_CREATED:
                markAdminUserMembershipCreated(now);
                break;

        }
    }

    // Step Pipeline 通过这些只读方法决定是否跳过该步骤
//    public boolean isOrganizationCreated() {
//        return keycloakOrganizationCreated;
//    }

    public boolean isPending(){
        return this.overallStatus == IamProvisioningStatus.IAM_PENDING;
    }

    public boolean isInProgress(){
        return this.overallStatus == IamProvisioningStatus.IAM_IN_PROGRESS;
    }

    public boolean isCompleted() {
        return this.overallStatus == IamProvisioningStatus.IAM_COMPLETED;
    }

    public boolean isFailed(){
        return this.overallStatus == IamProvisioningStatus.IAM_FAILED;
    }

    public boolean isAwaitingRetry(){
        return this.overallStatus == IamProvisioningStatus.IAM_AWAITING_RETRY;
    }

    public TenantIamProvisioningState snapshot() {
        TenantIamProvisioningState copy = new TenantIamProvisioningState(this.tenantId, this.workflowCorrelationId, this.createdAt);

        // 复制所有可变字段
        copy.overallStatus = this.overallStatus;
        copy.retryCount = this.retryCount;
        copy.version = this.version;
        copy.keycloakOrganizationCreated = this.keycloakOrganizationCreated;
        copy.adminUserCreated = this.adminUserCreated;
        copy.defaultRolesAssigned = this.defaultRolesAssigned;
        copy.adminUserMembershipCreated = this.adminUserMembershipCreated;
        copy.lastAttemptAt = this.lastAttemptAt;
        copy.nextRetryAt = this.nextRetryAt;
        copy.provisionedAt = this.provisionedAt;
        copy.provisioningFailureCode = this.provisioningFailureCode;
        copy.failureMessage = this.failureMessage;
        copy.updatedAt = this.updatedAt;
        // Instant 是不可变的，所以直接赋值是安全的，不需要再 copy
        return copy;
    }

    @Override
    public String toString() {
        return "TenantIamProvisioningState{tenantId=%s, status=%s, retryCount=%d, failureCode=%s, correlationId=%s}"
                .formatted(tenantId, overallStatus, retryCount, provisioningFailureCode, workflowCorrelationId);
    }

    public void restoreFrom(TenantIamProvisioningStateRow row) {
        this.overallStatus = IamProvisioningStatus.valueOf(row.iamStatus());
        this.retryCount = row.retryCount();
        this.failedAt =  row.failedAt();
        this.updatedAt = row.updatedAt();
        this.provisionedAt = row.provisionedAt();
        this.lastAttemptAt = row.lastAttemptAt();
        this.version = row.version();
        this.keycloakOrganizationCreated = row.keycloakOrganizationCreated();
        this.adminUserCreated = row.adminUserCreated();
        this.defaultRolesAssigned = row.defaultRolesAssigned();
        this.adminUserMembershipCreated = row.adminUserMembershipCreated();

        this.failureMessage = row.failureMessage();

    }
}


