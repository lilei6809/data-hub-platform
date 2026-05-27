package io.datahub.platform.iamprovisioning.application.mapper;

import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import org.springframework.stereotype.Component;

/**
 * 将入站跨 BC 事件翻译为本 BC 应用层指令（DesiredState）的映射器。
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li><b>负责</b>：字段搬运、默认值填充，把"外部语言"翻译为"本 BC 语言"。</li>
 *   <li><b>不负责</b>：产生任何副作用（无 I/O、无状态读写、无异常处理）。</li>
 *   <li><b>不负责</b>：决定默认值是什么——那是 {@link TenantIamDesiredState#ofMinimalInput}
 *       的知识，集中在领域层，这里只做传递。</li>
 * </ul>
 *
 * <h3>扩展点</h3>
 * <p>MVP 阶段所有租户使用统一的默认配置（DEFAULT identityMode + DEFAULT realmStrategy）。
 * 当需要按 tier 或租户属性切换策略时（如高级版 tier 使用独立 realm），
 * 在 {@link #from(TenantInfrastructureProvisionedEvent)} 中加入条件分支，
 * <b>不需要修改 Service 或 DesiredState</b>，保持了关注点分离。
 *
 * <h3>架构定位</h3>
 * <pre>
 * interfaces/messaging 层（Kafka Consumer）
 *        │ 接收 TenantInfrastructureProvisionedEvent
 *        ▼
 * TenantIamDesiredStateMapper  ←── 本类（application/mapper 层）
 *        │ 产出 TenantIamDesiredState
 *        ▼
 * ProvisionTenantIamUseCase    ←── application/port/in 层
 * </pre>
 */
@Component
public class TenantIamDesiredStateMapper {

    /**
     * 从上游触发事件推导 IAM Provisioning 的期望状态。
     *
     * <p>当前 MVP 映射规则：
     * <ul>
     *   <li>{@code tenantId}、{@code tenantName}、{@code tier}、{@code adminEmail}
     *       直接从事件中取，是 IAM Provisioning 的核心业务输入。</li>
     *   <li>{@code identityMode} = DEFAULT（不使用外部 IdP），由 {@code ofMinimalInput} 内置。</li>
     *   <li>{@code realmStrategy} = DEFAULT（共享 Realm），由 {@code ofMinimalInput} 内置。</li>
     *   <li>不传递 {@code correlationId}——correlationId 是追踪概念，不属于"期望业务状态"，
     *       由调用方独立传入 UseCase。</li>
     * </ul>
     *
     * @param event 由上游 BC 发布的基础设施就绪事件，必须非 null
     * @return 描述"我们想要什么 IAM 状态"的不可变对象，可直接传入 UseCase
     */
    public TenantIamDesiredState from(TenantInfrastructureProvisionedEvent event) {
        // ofMinimalInput 封装了"MVP 默认配置"的知识，扩展时只需替换此工厂方法调用
        return TenantIamDesiredState.ofMinimalInput(
                event.tenantId(),
                event.tenantName(),
                event.tier(),
                event.adminEmail()
        );
    }
}