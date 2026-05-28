package io.datahub.platform.iamprovisioning.domain.event;

import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;

import java.time.Instant;

/**
 * 跨 BC 边界的入站事件：表示"Tenant 基础设施已准备好，现在可以初始化其 IAM 资源"。
 *
 * <h3>事件流向</h3>
 * <pre>
 * Tenant Management BC          IAM Provisioning BC
 * ─────────────────────────────────────────────────
 * [TenantCreated]
 *    │ 创建云资源、DB schema...
 *    ▼
 * TenantInfrastructureProvisionedEvent ──Kafka──▶ TenantIamOnboardingEventHandler
 *                                                          │
 *                                                          ▼ (mapper)
 *                                                 TenantIamDesiredState
 *                                                          │
 *                                                          ▼ (service)
 *                                                 TenantIamProvisioningService
 * </pre>
 *
 * <h3>字段设计原则</h3>
 * <ul>
 *   <li>只携带本 BC 推导 IAM Provisioning 所需的最小业务事实，不包含基础设施内部 ID。</li>
 *   <li>{@code tenantName} 是创建 Keycloak Organization 的必要输入。</li>
 *   <li>{@code tier} 决定配额策略与 realm 分配策略，是 IAM 配置的关键维度。</li>
 *   <li>{@code correlationId} 在整条链路中端到端贯穿：事件 → 状态记录 → 输出事件 → 日志。</li>
 * </ul>
 *
 * <h3>版本演进约定</h3>
 * 当字段需要新增或修改时，应定义新版本（如 {@code TenantInfrastructureProvisionedEventV2}），
 * 而非直接修改此 record，以保证 Schema Registry 的向后兼容性。
 */
public record TenantInfrastructureProvisionedEvent(
        TenantId tenantId,           // 主业务标识，所有 BC 都认识，是跨 BC 的通用语言
        TenantName tenantName,       // 用于创建 Keycloak Organization 的显示名称
        Tier tier,                   // 决定 IAM 配置策略（如 realm 分配、配额）
        Email adminEmail,            // 初始管理员邮箱，IAM Provisioning 的直接业务输入
        CorrelationId correlationId, // 链路追踪 ID，必须贯穿到后续的状态记录和输出事件
        Instant occurredAt           // 事件发生时间，由发布方（Tenant Management BC）填写
) implements DomainEvent {

    /**
     * 静态工厂方法，供测试或内部代码直接构造事件实例。
     *
     * <p>在生产环境中，该事件由 Kafka 消费层反序列化创建，
     * 不需要直接调用此方法。
     */
    public static TenantInfrastructureProvisionedEvent of(
            TenantId tenantId,
            TenantName tenantName,
            Tier tier,
            Email adminEmail,
            CorrelationId correlationId,
            Instant occurredAt) {
        return new TenantInfrastructureProvisionedEvent(
                tenantId, tenantName, tier, adminEmail, correlationId, occurredAt);
    }



}