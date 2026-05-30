package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.in.ProvisionTenantIamUseCase;
import io.datahub.platform.iamprovisioning.application.port.out.EventPublisher;
import io.datahub.platform.iamprovisioning.application.port.out.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisioningFailedEvent;
import io.datahub.platform.iamprovisioning.domain.exception.EventPublishException;
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

    private final EventPublisher eventPublisher;

    public TenantIamProvisioningService(TenantIamProvisioningStateRepository repository,
                                        List<TenantIamProvisioningStep> ensureSteps,
                                        EventPublisher eventPublisher) {
        this.repository = repository;
        this.ensureSteps = ensureSteps;
        this.eventPublisher = eventPublisher;
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
        log.atInfo()
                .addKeyValue("event", "tenant_iam_provisioning_state_changed")
                .addKeyValue("tenantId", id)
                .addKeyValue("correlationId", correlationId)
                .addKeyValue("status", currentState.getOverallStatus())
                .addKeyValue("retryCount", currentState.getRetryCount())
                .log("Tenant IAM provisioning moved to in-progress");

        // 立刻持久化
        repository.save(currentState);

        // 为什么立刻 save？如果这之后进程崩溃，
        // RetryScheduler 重新拉起时，状态已是 InProgress，
        // 不会被重复触发（状态机保护）

        // === 阶段 2：执行 Steps（远程操作，不在事务内） ===
        // 初始化 context
        StepExecutionContext context = StepExecutionContext.init(id, correlationId);
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
            currentState.markCompleted(Instant.now());
            repository.save(currentState);

            //TODO: outbox pattern
            eventPublisher.publish(
                    TenantIamProvisionedEvent.of(
                            currentState.getTenantId(),
                            desired.tier(),
                            desired.adminUser().email(),
                            correlationId,
                            currentState.getProvisionedAt()
                    )
            );

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
            if (e.retryable()){
                currentState.markAwaitRetry(Instant.now(), e.failureCode(), e.getMessage());
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
                currentState.markFailed(Instant.now(), e.failureCode(), e.getMessage());
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

            repository.save(currentState);

            // lastAttemptAt 在 markAwaitRetry 和 markFailed 中均被设置，
            // 因此无论是可重试还是不可重试失败，它都不为 null，作为事件时间戳安全可用。
            // （failedAt 仅在 markFailed 时设置，对可重试路径为 null，不适合此处使用。）
            TenantIamProvisioningFailedEvent failedEvent = TenantIamProvisioningFailedEvent.of(
              currentState.getTenantId(),
              desired.tier(),
              e.failureCode(),
              e.retryable(),
              correlationId,
              currentState.getLastAttemptAt()
            );

            // 先发布事件，再抛出异常。
            // 如果顺序颠倒（先抛出），调用方 catch 后可能不再执行发布，导致下游永远感知不到失败。
            eventPublisher.publish(failedEvent);

            // 向上重新抛出，让调用方（Kafka Consumer / 测试）知道失败了，
            // 以便触发消息重试或死信队列
            throw e;
        } catch (RuntimeException e) {

            currentState.markFailed(Instant.now(), IamProvisioningFailureCode.UNKNOWN_ERROR, e.getMessage());
            repository.save(currentState);
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
