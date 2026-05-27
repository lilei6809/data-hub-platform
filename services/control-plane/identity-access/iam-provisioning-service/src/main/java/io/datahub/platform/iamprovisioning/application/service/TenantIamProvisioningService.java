package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.StepExecutionContext;
import io.datahub.platform.iamprovisioning.application.pipeline.TenantIamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.in.ProvisionTenantIamUseCase;
import io.datahub.platform.iamprovisioning.application.port.out.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

//TODO: 帮我加上专业的 log, 后期需要搭建可观测平台
@Service
public class TenantIamProvisioningService implements ProvisionTenantIamUseCase {

    private final TenantIamProvisioningStateRepository repository;
    private final List<TenantIamProvisioningStep> ensureSteps;



    public TenantIamProvisioningService(TenantIamProvisioningStateRepository repository, List<TenantIamProvisioningStep> ensureSteps) {
        this.repository = repository;
        this.ensureSteps = ensureSteps;
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
        if (currentState.isCompleted()) {
            return;
        }

        // 状态推进到 IN_PROGRESS, 表示"我认领了这个任务"
        // TODO: RetryScheduler 还未配置
        currentState.markInProgress(Instant.now());

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
                context = step.ensure(desired, context);
                currentState.markStepCompleted(step.checkpoint(), Instant.now());

                // 立刻持久化
                repository.save(currentState);
            }
            // 暂时在此控制 order, 因为代码设计有问题
            // 每一步结束, 我们需要保存当前的 state, 但是代码却没有 currentState 的更新
            // 并且 state 的字段并不包含 organizationId, userId 这些需要保存的信息[我们当前可以重新查询]


            // === 阶段 3：所有 Steps 成功，推进终态 ===
            currentState.markCompleted(Instant.now());
            repository.save(currentState);
        } catch (IamProvisioningException e) {
            if (e.retryable()){
                //TODO: log
                currentState.markAwaitRetry(Instant.now(), e.failureCode(), e.getMessage());

            } else  {
                currentState.markFailed(Instant.now(), e.failureCode(), e.getMessage());

            }

            repository.save(currentState);
            // 向上重新抛出，让调用方知道失败了
            throw e;
        } catch (RuntimeException e) {

            currentState.markFailed(Instant.now(), IamProvisioningFailureCode.UNKNOWN_ERROR, e.getMessage());
            repository.save(currentState);
            throw e;
        }
    }
}
