package io.datahub.platform.iamprovisioning.interfaces.messaging;

import io.datahub.platform.iamprovisioning.application.port.in.HandleTenantIamOnboardingEventUseCase;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.interfaces.messaging.dto.TenantInfrastructureProvisionedEventDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


/**
 * 仅仅供测试和手动触发使用
 * Inbound 适配器：接收来自外部（Kafka / 测试直调）的 {@link TenantInfrastructureProvisionedEvent}，
 * 转交给应用层 {@link HandleTenantIamOnboardingEventUseCase} 处理。
 *
 * <h3>架构定位（六边形架构 — interfaces 层薄适配器）</h3>
 * <p>本类的职责仅限于技术层面的"接收 + 转交"：
 * 拿到外部消息对象，直接调用 Driving Port。不持有任何业务逻辑或映射逻辑。</p>
 *
 * <pre>
 * 外部世界（Kafka record / 测试直调）
 *        │
 *        ▼
 * TenantIamOnboardingEventHandler  ← interfaces/messaging（本类，薄适配器）
 *          ACL: dto -> domain event
 *        │ useCase.handle(event)
 *        ▼
 * HandleTenantIamOnboardingEventUseCase  ← application/port/in（Driving Port）
 *        │ 实现：TenantIamOnboardingService
 *        ▼
 * TenantIamProvisioningService（Step Pipeline）
 * </pre>
 *
 * <h3>MVP 设计决策</h3>
 * <p>本类当前不加 {@code @KafkaListener}。
 * Phase 3 基础设施就绪后，只需在 {@link #handle} 方法上补注解，其余不变。</p>
 */
@Component
public class TenantIamOnboardingEventHandler {
    
    private final TenantInfrastructureProvisionedEventTranslator translator;

    // port.in
    private final HandleTenantIamOnboardingEventUseCase useCase;

    public TenantIamOnboardingEventHandler(HandleTenantIamOnboardingEventUseCase useCase) {
        this.translator = new  TenantInfrastructureProvisionedEventTranslator();
        this.useCase = useCase;
    }

    /**
     * 接收入站事件并转交给应用层处理。
     *
     * <p>异常不在此处吞掉，向上传播给调用方（Kafka Consumer / 测试）自行处理重试或死信队列。</p>
     *
     * @param eventDto 来自上游 BC 的基础设施就绪事件，不得为 null
     */
    @KafkaListener(topics = "cdp.infrastructure.tenant.provisioned")
    public void handle(TenantInfrastructureProvisionedEventDto eventDto) {
        TenantInfrastructureProvisionedEvent event = translator.translate(eventDto);
        useCase.handle(event);
    }
}