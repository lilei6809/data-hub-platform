package io.datahub.platform.iamprovisioning.interfaces.messaging;

import io.datahub.platform.iamprovisioning.application.mapper.TenantIamDesiredStateMapper;
import io.datahub.platform.iamprovisioning.application.port.in.ProvisionTenantIamUseCase;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IAM Onboarding 事件入口：消费来自上游 BC 的 {@link TenantInfrastructureProvisionedEvent}，
 * 将其翻译为 IAM Provisioning 指令并驱动应用服务执行。
 *
 * <h3>架构定位（六边形架构 — interfaces 层）</h3>
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │  外部世界（Kafka / 测试直调）                             │
 * │       │                                                  │
 * │       ▼                                                  │
 * │  TenantIamOnboardingEventHandler  ←── interfaces 层（本类）│
 * │       │ mapper.from(event)                               │
 * │       ▼                                                  │
 * │  TenantIamDesiredStateMapper      ←── application/mapper  │
 * │       │ useCase.provisionTenantIam(desiredState, corrId)  │
 * │       ▼                                                  │
 * │  ProvisionTenantIamUseCase（Port） ←── application/port/in │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>MVP 设计决策</h3>
 * <ul>
 *   <li>本类当前<b>不加</b> {@code @KafkaListener}。
 *       等 Phase 3 基础设施就绪后，只需在 {@link #handle} 方法上补注解，
 *       其余逻辑完全不变。</li>
 *   <li>异常<b>不在此处吞掉</b>，向上传播给调用方（未来的 Kafka Consumer / 测试）自行处理重试或死信。
 *       这样保证了：服务抛出的业务异常对消息系统可见，可以触发 DLQ（Dead Letter Queue）。</li>
 *   <li>{@code correlationId} 从事件中取而不重新生成，保证它与上游 BC 的追踪上下文一致。</li>
 * </ul>
 */
@Slf4j
@Component
public class TenantIamOnboardingEventHandler {

    // 翻译器：把跨 BC 的入站事件语义映射到本 BC 的应用层输入（纯函数，无副作用）
    private final TenantIamDesiredStateMapper mapper;

    // 应用层入口 Port：通过抽象依赖服务，不直接依赖实现，便于测试替换
    private final ProvisionTenantIamUseCase useCase;

    public TenantIamOnboardingEventHandler(
            TenantIamDesiredStateMapper mapper,
            ProvisionTenantIamUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    /**
     * 处理入站的 Tenant 基础设施就绪事件，驱动 IAM Provisioning 流程。
     *
     * <h4>执行顺序</h4>
     * <ol>
     *   <li>记录接收日志（含 {@code tenantId} 和 {@code correlationId}，便于链路追踪）。</li>
     *   <li>用映射器把事件翻译为 DesiredState（纯函数，不产生任何副作用）。</li>
     *   <li>驱动应用服务执行 IAM Provisioning。服务内部负责：
     *     <ul>
     *       <li>本地状态机推进（PENDING → IN_PROGRESS → COMPLETED / FAILED）</li>
     *       <li>Step Pipeline 执行（Keycloak 幂等操作）</li>
     *       <li>成功后发布 {@code TenantIamProvisionedEvent}</li>
     *       <li>失败后发布 {@code TenantIamProvisioningFailedEvent}</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param event 来自上游 BC 的基础设施就绪事件，不得为 null
     * @throws io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException
     *         当 IAM Provisioning 步骤失败时，由应用服务抛出并向上传播
     */
    public void handle(TenantInfrastructureProvisionedEvent event) {
        log.atInfo()
                .addKeyValue("event", "tenant_infra_provisioned_received")
                .addKeyValue("tenantId", event.tenantId())
                .addKeyValue("correlationId", event.correlationId())
                .addKeyValue("tier", event.tier())
                .addKeyValue("occurredAt", event.occurredAt())
                .log("Received TenantInfrastructureProvisionedEvent, starting IAM provisioning");

        // 步骤 1：翻译事件为应用层指令
        // mapper.from() 是纯函数：无 I/O、无状态、不抛受检异常，出错说明事件字段有问题
        TenantIamDesiredState desiredState = mapper.from(event);

        // 步骤 2：驱动应用服务执行全流程
        // correlationId 直接从事件中取，保证与上游 BC 的追踪上下文一致
        useCase.provisionTenantIam(desiredState, event.correlationId());
    }
}