package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.mapper.TenantIamDesiredStateMapper;
import io.datahub.platform.iamprovisioning.application.port.in.HandleTenantIamOnboardingEventUseCase;
import io.datahub.platform.iamprovisioning.application.port.in.ProvisionTenantIamUseCase;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 应用服务：实现 {@link HandleTenantIamOnboardingEventUseCase} Driving Port。
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>用 {@link TenantIamDesiredStateMapper} 将跨 BC 入站事件翻译为本 BC 领域指令（DesiredState）。</li>
 *   <li>将翻译结果委托给 {@link ProvisionTenantIamUseCase}，驱动 Step Pipeline 执行。</li>
 * </ol>
 *
 * <h3>为什么不把这段逻辑放在 interfaces/messaging 的适配器里</h3>
 * <p>"如何响应一个事件"是应用层的决策（翻译语言 + 驱动业务流程），不是适配器的职责。
 * 适配器只负责技术层面的转换（Kafka record → 领域事件对象），不应持有业务协调逻辑。
 * 若日后同一业务流程有多个触发来源（Kafka + HTTP + Scheduler），只需再加一个适配器实现此 Port，
 * 无需修改业务逻辑。</p>
 *
 * <h3>架构层次</h3>
 * <pre>
 * interfaces/messaging（薄适配器，技术翻译）
 *        │ 依赖 HandleTenantIamOnboardingEventUseCase（Port）
 *        ▼
 * TenantIamOnboardingService（本类，application/service）
 *        │ mapper.from(event)
 *        │ provisionUseCase.provisionTenantIam(desiredState, correlationId)
 *        ▼
 * TenantIamProvisioningService（Step Pipeline 协调）
 * </pre>
 */
@Slf4j
@Service
public class TenantIamOnboardingService implements HandleTenantIamOnboardingEventUseCase {

    // 纯函数翻译器：将跨 BC 入站事件语义映射到本 BC 的期望状态，无副作用
    private final TenantIamDesiredStateMapper mapper;

    // 下游用例：负责 Step Pipeline 执行、状态持久化、事件发布
    private final ProvisionTenantIamUseCase provisionUseCase;

    public TenantIamOnboardingService(
            TenantIamDesiredStateMapper mapper,
            ProvisionTenantIamUseCase provisionUseCase) {
        this.mapper = mapper;
        this.provisionUseCase = provisionUseCase;
    }

    /**
     * 接收 Tenant 基础设施就绪事件，翻译后驱动 IAM Provisioning 全流程。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>记录接收日志（tenantId + correlationId，便于链路追踪）。</li>
     *   <li>mapper.from(event)：纯函数翻译，产出 DesiredState。</li>
     *   <li>provisionUseCase.provisionTenantIam(...)：驱动业务流程。</li>
     * </ol>
     *
     * <p>correlationId 从事件中取而不重新生成，保证与上游 BC 的追踪上下文一致。
     * 异常不在此处吞掉，向上传播给调用方（Kafka Adapter / 测试）触发重试或死信。
     */
    @Override
    public void handle(TenantInfrastructureProvisionedEvent event) {
        log.atInfo()
                .addKeyValue("event", "tenant_infra_provisioned_received")
                .addKeyValue("tenantId", event.tenantId())
                .addKeyValue("correlationId", event.correlationId())
                .addKeyValue("tier", event.tier())
                .addKeyValue("occurredAt", event.occurredAt())
                .log("Received TenantInfrastructureProvisionedEvent, starting IAM provisioning");

        // 步骤 1：翻译事件为应用层指令（纯函数，无 I/O）
        TenantIamDesiredState desiredState = mapper.from(event);

        // 步骤 2：驱动应用服务执行全流程
        // correlationId 直接从事件中取，保证与上游 BC 的追踪上下文一致
        provisionUseCase.provisionTenantIam(desiredState, event.correlationId());
    }
}
