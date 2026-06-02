package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.in.ProvisionTenantIamUseCase;
import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.valueobject.ProvisioningEventContext;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class TenantIamProvisioningService implements ProvisionTenantIamUseCase {

    private final TenantIamProvisioningStateRepository repository;
    private final List<TenantIamProvisioningStep> ensureSteps;

    private final ProvisioningStateTransactor transactor;

    public TenantIamProvisioningService(TenantIamProvisioningStateRepository repository,
                                        List<TenantIamProvisioningStep> ensureSteps,
                                        ProvisioningStateTransactor transactor) {
        this.repository = repository;
        this.ensureSteps = ensureSteps;
        this.transactor = transactor;

    }

    /**
     * 认领工作流
     * @param desired 租户 IAM provisioning 的期望状态
     * @param correlationId  链路追踪
     */
    @Override
    public void provisionTenantIam(TenantIamDesiredState desired, CorrelationId correlationId) {
        TenantId id = desired.tenantId();

        // === 阶段 1：加载或初始化状态（本地操作，轻量） ===
        TenantIamProvisioningState currentState = repository.findOrInitById(id, correlationId);
        log.atInfo()
                .addKeyValue("event", "tenant_iam_provisioning_started")
                .addKeyValue("tenantId", id)
                .addKeyValue("correlationId", correlationId)
                .addKeyValue("currentStatus", currentState.getOverallStatus())
                .addKeyValue("retryCount", currentState.getRetryCount())
                .log("Tenant IAM provisioning started");

        // 状态短路
        if (currentState.isCompleted()) {
            log.atInfo()
                    .addKeyValue("event", "tenant_iam_provisioning_already_completed")
                    .addKeyValue("tenantId", id)
                    .addKeyValue("correlationId", correlationId)
                    .addKeyValue("status", currentState.getOverallStatus())
                    .addKeyValue("provisionedAt", currentState.getProvisionedAt())
                    .log("Tenant IAM provisioning skipped because state is already completed");
            return;
        }

        if (currentState.isInProgress()){
            log.atInfo()
                    .addKeyValue("event", "tenant_iam_provisioning_task_processed_by_others")
                    .addKeyValue("tenantId", id)
                    .addKeyValue("correlationId", correlationId)
                    .addKeyValue("status", currentState.getOverallStatus())
                    .addKeyValue("provisionedAt", currentState.getProvisionedAt())
                    .log("Tenant IAM provisioning task is being processed by other service instance");
            return;
        }

        // 状态推进到 IN_PROGRESS, 表示"我认领了这个任务"
        // TODO: RetryScheduler 还未配置
        currentState.markInProgress(Instant.now());
        // 立刻持久化
        repository.save(currentState); // database version = 1

        // ********* 如果程序此时崩溃, 数据库中有 tenant_abc 的 IN_PROGRESS
        // 消息队列中 tenant_abc 的消费未提交, 仍然在 broker
        // instance 重新启动, 准备消费 tenant_abc, 数据库中有记录 IN_PROGRESS, consumer ack
        // 但是 RetryScheduler 只会扫描 IAM_AWAITING_RETRY, 不会扫描 IN_PROGRESS(可能别的线程在处理)
        // 所以 IN_PROGRESS 就会变得无人认领
        // 所以需要认领和 reclaim 的机制

        log.atInfo()
                .addKeyValue("event", "tenant_iam_provisioning_state_changed")
                .addKeyValue("tenantId", id)
                .addKeyValue("correlationId", correlationId)
                .addKeyValue("status", currentState.getOverallStatus())
                .addKeyValue("retryCount", currentState.getRetryCount())
                .log("Tenant IAM provisioning moved to in-progress");



        // 为什么立刻 save？如果这之后进程崩溃，
        // RetryScheduler 重新拉起时，状态已是 InProgress，
        // 不会被重复触发（状态机保护）

        // === 阶段 2：执行 Steps（远程操作，不在事务内） ===
        // 初始化 context
        StepExecutionContext context = StepExecutionContext.init(id, correlationId);
        ProvisioningEventContext eventContext = ProvisioningEventContext.of(
                desired.tier(),
                desired.adminUser().email(),
                correlationId
        );;
        try {
            for (TenantIamProvisioningStep step : ensureSteps) {
                log.atInfo()
                        .addKeyValue("event", "tenant_iam_provisioning_step_started")
                        .addKeyValue("tenantId", id)
                        .addKeyValue("correlationId", correlationId)
                        .addKeyValue("step", step.name())
                        .addKeyValue("checkpoint", step.checkpoint())
                        .log("Tenant IAM provisioning step started");

                context = step.ensure(desired, context);

                // 我们不知道当前 具体是哪一个 step, 但是 Step 自身知道自己是哪一个 step
                currentState.markStepCompleted(step.checkpoint(), Instant.now());

                // 立刻持久化
                repository.save(currentState);
                log.atInfo()
                        .addKeyValue("event", "tenant_iam_provisioning_step_completed")
                        .addKeyValue("tenantId", id)
                        .addKeyValue("correlationId", correlationId)
                        .addKeyValue("step", step.name())
                        .addKeyValue("checkpoint", step.checkpoint())
                        .addKeyValue("status", currentState.getOverallStatus())
                        .addKeyValue("organizationId", context.getOrganizationId().map(Object::toString).orElse(null))
                        .addKeyValue("userId", context.getUserId().map(Object::toString).orElse(null))
                        .log("Tenant IAM provisioning step completed");
            }


            // === 阶段 3：所有 Steps 成功，推进终态 ===
            currentState.markCompleted(Instant.now(), eventContext);

            transactor.saveIamProvisionStateAndAppendEvents(currentState);



            log.atInfo()
                    .addKeyValue("event", "tenant_iam_provisioning_completed")
                    .addKeyValue("tenantId", id)
                    .addKeyValue("correlationId", correlationId)
                    .addKeyValue("status", currentState.getOverallStatus())
                    .addKeyValue("provisionedAt", currentState.getProvisionedAt())
                    .addKeyValue("organizationCreated", currentState.isKeycloakOrganizationCreated())
                    .addKeyValue("adminUserCreated", currentState.isAdminUserCreated())
                    .addKeyValue("defaultRolesAssigned", currentState.isDefaultRolesAssigned())
                    .addKeyValue("adminUserMembershipCreated", currentState.isAdminUserMembershipCreated())
                    .log("Tenant IAM provisioning completed");
        }

        catch (IamProvisioningException e) {
            // service 层不知道具体的技术异常是什么, 只知道领域业务异常, 以及异常是否可重试
            if (e.retryable()){
                currentState.markAwaitRetry(Instant.now(), e.failureCode(), e.getMessage(), eventContext);
                log.atWarn()
                        .addKeyValue("event", "tenant_iam_provisioning_awaiting_retry")
                        .addKeyValue("tenantId", id)
                        .addKeyValue("correlationId", correlationId)
                        .addKeyValue("step", e.step())
                        .addKeyValue("failureCode", e.failureCode())
                        .addKeyValue("retryable", true)
                        .addKeyValue("status", currentState.getOverallStatus())
                        .addKeyValue("retryCount", currentState.getRetryCount())
                        .addKeyValue("nextRetryAt", currentState.getNextRetryAt())
                        .log("Tenant IAM provisioning failed with retryable error");
            } else  {
                currentState.markFailed(Instant.now(), e.failureCode(), e.getMessage(), eventContext);
                log.atError()
                        .addKeyValue("event", "tenant_iam_provisioning_failed")
                        .addKeyValue("tenantId", id)
                        .addKeyValue("correlationId", correlationId)
                        .addKeyValue("step", e.step())
                        .addKeyValue("failureCode", e.failureCode())
                        .addKeyValue("retryable", false)
                        .addKeyValue("status", currentState.getOverallStatus())
                        .addKeyValue("retryCount", currentState.getRetryCount())
                        .log("Tenant IAM provisioning failed with non-retryable error");

            }


            transactor.saveIamProvisionStateAndAppendEvents(currentState);

            // 向上重新抛出，让调用方（Kafka Consumer / 测试）知道失败了，
            // 以便触发消息重试或死信队列
            throw e;
        } catch (RuntimeException e) {

            currentState.markFailed(Instant.now(), IamProvisioningFailureCode.UNKNOWN_ERROR, e.getMessage(), eventContext);

            transactor.saveIamProvisionStateAndAppendEvents(currentState);
            log.atError()
                    .addKeyValue("event", "tenant_iam_provisioning_failed")
                    .addKeyValue("tenantId", id)
                    .addKeyValue("correlationId", correlationId)
                    .addKeyValue("failureCode", IamProvisioningFailureCode.UNKNOWN_ERROR)
                    .addKeyValue("exceptionType", e.getClass().getName())
                    .addKeyValue("status", currentState.getOverallStatus())
                    .addKeyValue("retryCount", currentState.getRetryCount())
                    .log("Tenant IAM provisioning failed with unexpected error");
            throw e;
        }
    }
}
