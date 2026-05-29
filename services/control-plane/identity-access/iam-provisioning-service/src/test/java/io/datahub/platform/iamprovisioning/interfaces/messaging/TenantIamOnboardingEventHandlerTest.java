package io.datahub.platform.iamprovisioning.interfaces.messaging;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.mapper.TenantIamDesiredStateMapper;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureAdminUserStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationMembershipStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureTenantAdminRoleStep;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakAuthenticationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakTransientException;
import io.datahub.platform.iamprovisioning.application.service.TenantIamOnboardingService;
import io.datahub.platform.iamprovisioning.application.service.TenantIamProvisioningService;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisioningFailedEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.FakeKeycloakAdminPort;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.KeycloakOperation;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.InMemoryEventPublisher;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryTenantIamProvisioningStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 端到端集成测试：验证以 {@link TenantInfrastructureProvisionedEvent} 为入口的完整 IAM Onboarding 流程。
 *
 * <h3>测试目标</h3>
 * 验证事件驱动的闭环：
 * <pre>
 * TenantInfrastructureProvisionedEvent
 *        │ (handler.handle)
 *        ▼
 * TenantIamDesiredStateMapper
 *        │ (useCase.provisionTenantIam)
 *        ▼
 * TenantIamProvisioningService → Step Pipeline (Fake Keycloak)
 *        │
 *        ├── 成功 → TenantIamProvisionedEvent 出现在 InMemoryEventPublisher
 *        └── 失败 → TenantIamProvisioningFailedEvent 出现在 InMemoryEventPublisher
 * </pre>
 *
 * <h3>基础设施选择</h3>
 * 全部使用内存实现（FakeKeycloakAdminPort、InMemoryRepository、InMemoryEventPublisher），
 * 不依赖任何外部服务，可快速执行，适合 CI 环境。
 */
class TenantIamOnboardingEventHandlerTest {

    // 被测对象：事件处理器，是整个流程的唯一入口
    private TenantIamOnboardingEventHandler handler;

    // 基础设施 Fake 实现，测试中可直接读取其内部状态进行断言
    private FakeKeycloakAdminPort fakeKeycloak;
    private InMemoryTenantIamProvisioningStateRepository repository;
    private InMemoryEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // 组装所有依赖：这里展示了完整的对象图，也证明了无需 Spring 容器即可运行
        fakeKeycloak = new FakeKeycloakAdminPort();
        repository = new InMemoryTenantIamProvisioningStateRepository();
        eventPublisher = new InMemoryEventPublisher();

        TenantIamProvisioningService service = new TenantIamProvisioningService(
                repository,
                List.of(
                        new EnsureOrganizationStep(fakeKeycloak),
                        new EnsureAdminUserStep(fakeKeycloak),
                        new EnsureTenantAdminRoleStep(fakeKeycloak),
                        new EnsureOrganizationMembershipStep(fakeKeycloak)
                ),
                eventPublisher
        );

        // TenantIamOnboardingService 持有 mapper，实现 HandleTenantIamOnboardingEventUseCase Port
        TenantIamOnboardingService onboardingService = new TenantIamOnboardingService(
                new TenantIamDesiredStateMapper(),
                service
        );

        // 薄适配器：仅持有 Port，不含任何业务逻辑
        handler = new TenantIamOnboardingEventHandler(onboardingService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 1：正常成功路径
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("成功路径：接收到入站事件后，应完成 IAM Provisioning 并发布 TenantIamProvisionedEvent")
    void handle_shouldCompleteProvisioningAndPublishSuccessEvent_whenNoFailures() {
        // 构造入站事件（模拟从上游 Tenant Management BC 接收）
        TenantInfrastructureProvisionedEvent inboundEvent = TenantInfrastructureProvisionedEvent.of(
                TenantId.of("tenant-acme"),
                TenantName.of("Acme Corp"),
                Tier.of("BASIC"),
                Email.of("admin@acme.com"),
                CorrelationId.newCorrelationId(),
                Instant.now()
        );

        // 驱动被测流程
        handler.handle(inboundEvent);

        // === 断言 1：本地状态机最终进入 COMPLETED ===
        TenantIamProvisioningState state = repository.findByTenantId(inboundEvent.tenantId()).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);
        assertThat(state.isKeycloakOrganizationCreated()).isTrue();
        assertThat(state.isAdminUserCreated()).isTrue();
        assertThat(state.isDefaultRolesAssigned()).isTrue();
        assertThat(state.isAdminUserMembershipCreated()).isTrue();

        // === 断言 2：Keycloak（Fake）中确实创建了对应的对象 ===
        assertThat(fakeKeycloak.organizationsSnapshot()).hasSize(1)
                .containsKey(inboundEvent.tenantId());
        assertThat(fakeKeycloak.usersSnapshot()).hasSize(1)
                .containsKey(inboundEvent.adminEmail());

        // === 断言 3：成功事件已发布，字段与输入事件一致 ===
        // 验证事件驱动的输出闭环：有事件进来，就有事件出去
        List<TenantIamProvisionedEvent> publishedEvents =
                eventPublisher.getPublishedEvents(TenantIamProvisionedEvent.class);
        assertThat(publishedEvents).hasSize(1);

        TenantIamProvisionedEvent successEvent = publishedEvents.get(0);
        assertThat(successEvent.tenantId()).isEqualTo(inboundEvent.tenantId());
        assertThat(successEvent.tier()).isEqualTo(inboundEvent.tier());
        assertThat(successEvent.adminEmail()).isEqualTo(inboundEvent.adminEmail());
        // correlationId 贯穿：入站事件的 correlationId 出现在出站事件中
        assertThat(successEvent.correlationId()).isEqualTo(inboundEvent.correlationId());
        assertThat(successEvent.occurredAt()).isNotNull();

        // === 断言 4：没有错误事件被发布 ===
        assertThat(eventPublisher.getPublishedEvents(TenantIamProvisioningFailedEvent.class)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 2：可重试失败路径
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("可重试失败路径：Step 出现瞬时故障时，应进入 AWAITING_RETRY 并发布失败事件（retryable=true）")
    void handle_shouldPublishRetryableFailedEvent_whenStepThrowsTransientError() {
        // 注入故障：ENSURE_ORGANIZATION_MEMBERSHIP 第 1 次调用时抛出瞬时异常（模拟 Keycloak 暂时不可用）
        fakeKeycloak.scheduleFailures(
                KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                1,
                new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, TenantId.of("tenant-beta"), null)
        );

        TenantInfrastructureProvisionedEvent inboundEvent = TenantInfrastructureProvisionedEvent.of(
                TenantId.of("tenant-beta"),
                TenantName.of("Beta Inc"),
                Tier.of("BASIC"),
                Email.of("admin@beta.com"),
                CorrelationId.newCorrelationId(),
                Instant.now()
        );

        // 服务应抛出可重试异常，让调用方（Kafka Consumer）决定何时重试
        assertThatThrownBy(() -> handler.handle(inboundEvent))
                .isInstanceOf(IamProvisioningException.class)
                .satisfies(ex -> {
                    IamProvisioningException e = (IamProvisioningException) ex;
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE);
                });

        // === 断言 1：本地状态为 AWAITING_RETRY（不是 FAILED）===
        TenantIamProvisioningState state = repository.findByTenantId(inboundEvent.tenantId()).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_AWAITING_RETRY);
        assertThat(state.getRetryCount()).isEqualTo(1);
        assertThat(state.getNextRetryAt()).isNotNull();   // 退避重试时间点已计算

        // === 断言 2：失败事件已发布，retryable=true ===
        // 这是关键验证：下游审计或告警系统能区分"瞬时失败需等待"与"永久失败需人工处理"
        List<TenantIamProvisioningFailedEvent> failedEvents =
                eventPublisher.getPublishedEvents(TenantIamProvisioningFailedEvent.class);
        assertThat(failedEvents).hasSize(1);

        TenantIamProvisioningFailedEvent failedEvent = failedEvents.get(0);
        assertThat(failedEvent.tenantId()).isEqualTo(inboundEvent.tenantId());
        assertThat(failedEvent.tier()).isEqualTo(inboundEvent.tier());
        assertThat(failedEvent.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE);
        assertThat(failedEvent.retryable()).isTrue();     // 下游据此决定等待重试而非发告警
        assertThat(failedEvent.correlationId()).isEqualTo(inboundEvent.correlationId());

        // === 断言 3：没有成功事件被发布 ===
        assertThat(eventPublisher.getPublishedEvents(TenantIamProvisionedEvent.class)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 3：不可重试失败路径
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("不可重试失败路径：Step 出现认证错误时，应进入 IAM_FAILED 并发布失败事件（retryable=false）")
    void handle_shouldPublishNonRetryableFailedEvent_whenStepThrowsAuthError() {
        // 注入故障：认证异常（如凭证撤销或权限变更），不应重试
        fakeKeycloak.scheduleFailures(
                KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                1,
                new KeycloakAuthenticationException(TenantId.of("tenant-gamma"), KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, "forbidden", null)
        );

        TenantInfrastructureProvisionedEvent inboundEvent = TenantInfrastructureProvisionedEvent.of(
                TenantId.of("tenant-gamma"),
                TenantName.of("Gamma Ltd"),
                Tier.of("BASIC"),
                Email.of("admin@gamma.com"),
                CorrelationId.newCorrelationId(),
                Instant.now()
        );

        // 服务应抛出不可重试异常
        assertThatThrownBy(() -> handler.handle(inboundEvent))
                .isInstanceOf(IamProvisioningException.class)
                .satisfies(ex -> {
                    IamProvisioningException e = (IamProvisioningException) ex;
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED);
                });

        // === 断言 1：本地状态为 IAM_FAILED（终态，需人工介入）===
        TenantIamProvisioningState state = repository.findByTenantId(inboundEvent.tenantId()).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_FAILED);
        assertThat(state.getNextRetryAt()).isNull();    // 不可重试：无退避时间点

        // === 断言 2：失败事件已发布，retryable=false ===
        // 下游告警系统据此立刻触发 PagerDuty / Slack 告警，不等待重试
        List<TenantIamProvisioningFailedEvent> failedEvents =
                eventPublisher.getPublishedEvents(TenantIamProvisioningFailedEvent.class);
        assertThat(failedEvents).hasSize(1);

        TenantIamProvisioningFailedEvent failedEvent = failedEvents.get(0);
        assertThat(failedEvent.tenantId()).isEqualTo(inboundEvent.tenantId());
        assertThat(failedEvent.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED);
        assertThat(failedEvent.retryable()).isFalse();   // 下游据此立刻告警，不等待重试
        assertThat(failedEvent.correlationId()).isEqualTo(inboundEvent.correlationId());

        // === 断言 3：没有成功事件被发布 ===
        assertThat(eventPublisher.getPublishedEvents(TenantIamProvisionedEvent.class)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 4：可重试失败后恢复（幂等重试）
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("幂等恢复：可重试失败后重新触发，应成功完成 Provisioning 并发布成功事件")
    void handle_shouldRecoverIdempotently_afterRetryableFailure() {
        TenantId tenantId = TenantId.of("tenant-delta");
        Email adminEmail = Email.of("admin@delta.com");
        CorrelationId firstCorrelationId = CorrelationId.newCorrelationId();

        // 第一次触发：注入一次瞬时故障
        fakeKeycloak.scheduleFailures(
                KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                1,
                new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, tenantId, null)
        );

        TenantInfrastructureProvisionedEvent firstEvent = TenantInfrastructureProvisionedEvent.of(
                tenantId, TenantName.of("Delta Corp"), Tier.of("BASIC"),
                adminEmail, firstCorrelationId, Instant.now()
        );

        // 第一次调用失败（预期行为）
        assertThatThrownBy(() -> handler.handle(firstEvent))
                .isInstanceOf(IamProvisioningException.class);

        // 验证失败事件已发布
        assertThat(eventPublisher.getPublishedEvents(TenantIamProvisioningFailedEvent.class)).hasSize(1);

        // 第二次触发：用新的 correlationId 重试（模拟 RetryScheduler 重新发起）
        // 故障已消除（未注入新故障），应成功
        CorrelationId retryCorrelationId = CorrelationId.newCorrelationId();
        TenantInfrastructureProvisionedEvent retryEvent = TenantInfrastructureProvisionedEvent.of(
                tenantId, TenantName.of("Delta Corp"), Tier.of("BASIC"),
                adminEmail, retryCorrelationId, Instant.now()
        );

        handler.handle(retryEvent);

        // === 断言 1：最终状态 COMPLETED ===
        TenantIamProvisioningState state = repository.findByTenantId(tenantId).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);

        // === 断言 2：成功事件已发布，使用重试时的 correlationId ===
        List<TenantIamProvisionedEvent> successEvents =
                eventPublisher.getPublishedEvents(TenantIamProvisionedEvent.class);
        assertThat(successEvents).hasSize(1);
        assertThat(successEvents.get(0).correlationId()).isEqualTo(retryCorrelationId);

        // === 断言 3：Keycloak 中只有一份数据（幂等性：前两个步骤未重复创建）===
        assertThat(fakeKeycloak.organizationsSnapshot()).hasSize(1);
        assertThat(fakeKeycloak.usersSnapshot()).hasSize(1);
    }
}