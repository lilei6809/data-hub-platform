package io.datahub.platform.iamprovisioning.interfaces.http;

import io.datahub.platform.iamprovisioning.application.port.in.HandleTenantIamOnboardingEventUseCase;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * HTTP Inbound Adapter：接收管理员手动触发的 IAM Provisioning 请求。
 *
 * <p>适配器职责：将 HTTP 语言（路径变量 + 请求体）翻译为领域对象，交给 Port，不含业务逻辑。</p>
 *
 * <p>与 {@code TenantIamOnboardingEventHandler}（Kafka Adapter）共享同一个
 * {@link HandleTenantIamOnboardingEventUseCase} Port，体现了六边形架构的核心收益：
 * 同一业务能力可被多个入站渠道触发，应用核心完全不感知渠道差异。</p>
 */
@RestController
public class TenantIamProvisioningController {

    private final HandleTenantIamOnboardingEventUseCase useCase;

    public TenantIamProvisioningController(HandleTenantIamOnboardingEventUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 手动触发指定租户的 IAM Provisioning 流程。
     *
     * <p>使用场景：运维人员在 Kafka 消费失败或需要强制重跑时，通过管理接口直接触发。</p>
     *
     * @param tenantId 路径变量中的租户标识
     * @param request  包含 tenantName、tier、adminEmail 的请求体
     */
    @PostMapping("/admin/tenants/{tenantId}/provision-iam")
    public void manualTriggerIamProvisioning(
            @PathVariable String tenantId,
            @RequestBody ManualProvisionIamRequest request) {

        // Adapter 层:  HTTP 语言 → 领域对象（适配器唯一职责）[纯技术翻译]
        TenantInfrastructureProvisionedEvent event = TenantInfrastructureProvisionedEvent.of(
                TenantId.of(tenantId),
                TenantName.of(request.tenantName()),
                Tier.of(request.tier()),
                Email.of(request.adminEmail()),
                CorrelationId.newCorrelationId(),
                Instant.now()
        );

        useCase.handle(event);
    }
}