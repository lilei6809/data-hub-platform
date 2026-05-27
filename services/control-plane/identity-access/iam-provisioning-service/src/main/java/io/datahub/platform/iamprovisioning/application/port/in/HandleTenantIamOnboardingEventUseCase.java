package io.datahub.platform.iamprovisioning.application.port.in;

import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;

/**
 * Driving Port：声明应用核心"处理 Tenant 基础设施就绪事件"的能力。
 *
 * <h3>架构定位（六边形架构 — Driving Port）</h3>
 * <p>Driving Port 是应用核心对外部世界的能力声明，用领域语言表达。
 * 外部世界（Kafka Consumer、HTTP Controller、测试）通过此接口驱动应用，
 * 应用核心实现此接口，外部适配器依赖此接口——方向不反转。</p>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  外部世界（Kafka Adapter / 测试直调）                         │
 * │       │ 依赖此接口，不依赖实现                                │
 * │       ▼                                                      │
 * │  HandleTenantIamOnboardingEventUseCase  ← Driving Port（本接口）│
 * │       │ 实现者：TenantIamOnboardingService（application/service）│
 * │       ▼                                                      │
 * │  TenantIamOnboardingService                                  │
 * │       │ mapper.from(event) + provisionUseCase.provision...   │
 * │       ▼                                                      │
 * │  TenantIamProvisioningService（Step Pipeline 等）             │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>为什么放在 port/in 而非 interfaces/messaging</h3>
 * <p>Port 是应用核心的能力边界，必须用领域语言定义在应用核心内。
 * 放在 port/in 意味着：应用核心知道自己能做什么，但不知道谁来调用、怎么调用。
 * 反之若声明在 interfaces，应用核心就"看见"了外部基础设施，违反了依赖倒置原则。</p>
 */
public interface HandleTenantIamOnboardingEventUseCase {

    /**
     * 处理入站的 Tenant 基础设施就绪事件，驱动 IAM Provisioning 全流程。
     *
     * @param event 来自上游 BC 的基础设施就绪事件，不得为 null
     * @throws io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException
     *         当 IAM Provisioning 步骤失败时抛出并向上传播，让调用方决定重试或死信
     */
    void handle(TenantInfrastructureProvisionedEvent event);
}