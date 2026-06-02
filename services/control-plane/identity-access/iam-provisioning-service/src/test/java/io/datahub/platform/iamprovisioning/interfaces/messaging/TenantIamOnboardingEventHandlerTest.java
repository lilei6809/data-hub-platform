package io.datahub.platform.iamprovisioning.interfaces.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.mapper.TenantIamDesiredStateMapper;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureAdminUserStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationMembershipStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureTenantAdminRoleStep;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.service.ProvisioningStateTransactor;
import io.datahub.platform.iamprovisioning.config.kafka.properties.KafkaTopicProperties;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.exception.KeycloakAuthenticationException;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.exception.KeycloakTransientException;
import io.datahub.platform.iamprovisioning.application.service.TenantIamOnboardingService;
import io.datahub.platform.iamprovisioning.application.service.TenantIamProvisioningService;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.FakeKeycloakAdminPort;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.KeycloakOperation;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryOutboxRepository;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryTenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.interfaces.messaging.dto.TenantInfrastructureProvisionedEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 端到端集成测试：验证以 {@link TenantInfrastructureProvisionedEventDto} 为入口的完整 IAM Onboarding 流程。
 *
 * <h3>测试目标</h3>
 * 验证事件驱动的闭环：
 * <pre>
 * TenantInfrastructureProvisionedEventDto（外部契约 DTO）
 *        │ translator.translate(dto)        ← ACL 防腐翻译（Handler 内部）
 *        ▼
 * TenantInfrastructureProvisionedEvent（领域事件）
 *        │ (useCase.handle)
 *        ▼
 * TenantIamDesiredStateMapper
 *        │ (provisionTenantIam)
 *        ▼
 * TenantIamProvisioningService → Step Pipeline (Fake Keycloak)
 *        │
 *        ├── 成功 → TenantIamProvisionedEvent 写入 OutboxRepository
 *        └── 失败 → TenantIamProvisioningFailedEvent 写入 OutboxRepository
 * </pre>
 *
 * <h3>基础设施选择</h3>
 * 全部使用内存实现（FakeKeycloakAdminPort、InMemoryRepository、InMemoryOutboxRepository），
 * 不依赖任何外部服务，可快速执行，适合 CI 环境。
 */
class TenantIamOnboardingEventHandlerTest {

    private TenantIamOnboardingEventHandler handler;

    private FakeKeycloakAdminPort fakeKeycloak;
    private InMemoryTenantIamProvisioningStateRepository stateRepository;
    private InMemoryOutboxRepository outboxRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        fakeKeycloak = new FakeKeycloakAdminPort();
        stateRepository = new InMemoryTenantIamProvisioningStateRepository();
        outboxRepository = new InMemoryOutboxRepository();
        objectMapper = new ObjectMapper();

        KafkaTopicProperties kafkaTopicProperties = new KafkaTopicProperties();
        kafkaTopicProperties.setTenantIamProvisioned("test.cdp.iam.provisioned");
        kafkaTopicProperties.setTenantIamProvisionFailed("test.cdp.iam.provision-failed");

        ProvisioningStateTransactor transactor = new ProvisioningStateTransactor(
                stateRepository, outboxRepository, objectMapper, kafkaTopicProperties);

        TenantIamProvisioningService service = new TenantIamProvisioningService(
                stateRepository,
                List.of(
                        new EnsureOrganizationStep(fakeKeycloak),
                        new EnsureAdminUserStep(fakeKeycloak),
                        new EnsureTenantAdminRoleStep(fakeKeycloak),
                        new EnsureOrganizationMembershipStep(fakeKeycloak)
                ), transactor);

        TenantIamOnboardingService onboardingService = new TenantIamOnboardingService(
                new TenantIamDesiredStateMapper(),
                service);

        handler = new TenantIamOnboardingEventHandler(onboardingService);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 1：正常成功路径
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("成功路径：接收到入站 DTO，经 ACL 翻译后完成 IAM Provisioning 并发布 TenantIamProvisionedEvent")
    void handle_shouldCompleteProvisioningAndPublishSuccessEvent_whenNoFailures() {
        String tenantId = "tenant-acme";
        String email = "admin@acme.com";
        String correlationId = UUID.randomUUID().toString();

        TenantInfrastructureProvisionedEventDto dto = new TenantInfrastructureProvisionedEventDto(
                tenantId, "Acme Corp", "BASIC", email, correlationId, Instant.now().toString()
        );

        handler.handle(dto);

        // === 断言 1：本地状态机最终进入 COMPLETED ===
        TenantIamProvisioningState state = stateRepository.findByTenantId(TenantId.of(tenantId)).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);
        assertThat(state.isKeycloakOrganizationCreated()).isTrue();
        assertThat(state.isAdminUserCreated()).isTrue();
        assertThat(state.isDefaultRolesAssigned()).isTrue();
        assertThat(state.isAdminUserMembershipCreated()).isTrue();

        // === 断言 2：Keycloak（Fake）中确实创建了对应的对象 ===
        assertThat(fakeKeycloak.organizationsSnapshot()).hasSize(1)
                .containsKey(TenantId.of(tenantId));
        assertThat(fakeKeycloak.usersSnapshot()).hasSize(1)
                .containsKey(Email.of(email));

        // === 断言 3：成功事件已写入 Outbox ===
        List<OutBoxEvent> outBoxEvents = outboxRepository.allEvents();
        assertThat(outBoxEvents).hasSize(1);

        OutBoxEvent successEvent = outBoxEvents.getFirst();
        assertThat(successEvent.eventType()).isEqualTo("TenantIamProvisionedEvent");
        assertThat(successEvent.aggregateId()).isEqualTo(tenantId);
        assertThat(successEvent.correlationId()).isEqualTo(correlationId);
        assertThat(successEvent.occurredAt()).isNotNull();

        // === 断言 4：没有失败事件被写入 ===
        assertThat(outboxRepository.eventsByType("TenantIamProvisioningFailedEvent")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 2：可重试失败路径
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("可重试失败路径：Step 出现瞬时故障时，应进入 AWAITING_RETRY 并发布失败事件（retryable=true）")
    void handle_shouldPublishRetryableFailedEvent_whenStepThrowsTransientError() throws Exception {
        String tenantId = "tenant-beta";
        fakeKeycloak.scheduleFailures(
                KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                1,
                new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, TenantId.of(tenantId), null)
        );

        String correlationId = UUID.randomUUID().toString();
        TenantInfrastructureProvisionedEventDto dto = new TenantInfrastructureProvisionedEventDto(
                tenantId, "Beta Inc", "BASIC", "admin@beta.com", correlationId, Instant.now().toString()
        );

        assertThatThrownBy(() -> handler.handle(dto))
                .isInstanceOf(IamProvisioningException.class)
                .satisfies(ex -> {
                    IamProvisioningException e = (IamProvisioningException) ex;
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE);
                });

        // === 断言 1：本地状态为 AWAITING_RETRY ===
        TenantIamProvisioningState state = stateRepository.findByTenantId(TenantId.of(tenantId)).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_AWAITING_RETRY);
        assertThat(state.getRetryCount()).isEqualTo(1);
        assertThat(state.getNextRetryAt()).isNotNull();

        // === 断言 2：失败事件已写入 Outbox，retryable=true ===
        List<OutBoxEvent> failedOutboxEvents = outboxRepository.eventsByType("TenantIamProvisioningFailedEvent");
        assertThat(failedOutboxEvents).hasSize(1);

        OutBoxEvent failedOutboxEvent = failedOutboxEvents.getFirst();
        assertThat(failedOutboxEvent.aggregateId()).isEqualTo(tenantId);
        assertThat(failedOutboxEvent.correlationId()).isEqualTo(correlationId);

        JsonNode payload = objectMapper.readTree(failedOutboxEvent.payload());
        assertThat(payload.get("failureCode").asText()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE.name());
        assertThat(payload.get("retryable").asText()).isEqualTo("true");

        // === 断言 3：没有成功事件被写入 ===
        assertThat(outboxRepository.eventsByType("TenantIamProvisionedEvent")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 3：不可重试失败路径
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("不可重试失败路径：Step 出现认证错误时，应进入 IAM_FAILED 并发布失败事件（retryable=false）")
    void handle_shouldPublishNonRetryableFailedEvent_whenStepThrowsAuthError() throws Exception {
        String tenantId = "tenant-gamma";
        fakeKeycloak.scheduleFailures(
                KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                1,
                new KeycloakAuthenticationException(TenantId.of(tenantId), KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, "forbidden", null)
        );

        String correlationId = UUID.randomUUID().toString();
        TenantInfrastructureProvisionedEventDto dto = new TenantInfrastructureProvisionedEventDto(
                tenantId, "Gamma Ltd", "BASIC", "admin@gamma.com", correlationId, Instant.now().toString()
        );

        assertThatThrownBy(() -> handler.handle(dto))
                .isInstanceOf(IamProvisioningException.class)
                .satisfies(ex -> {
                    IamProvisioningException e = (IamProvisioningException) ex;
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED);
                });

        // === 断言 1：本地状态为 IAM_FAILED（终态） ===
        TenantIamProvisioningState state = stateRepository.findByTenantId(TenantId.of(tenantId)).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_FAILED);
        assertThat(state.getNextRetryAt()).isNull();

        // === 断言 2：失败事件已写入 Outbox，retryable=false ===
        List<OutBoxEvent> failedOutboxEvents = outboxRepository.eventsByType("TenantIamProvisioningFailedEvent");
        assertThat(failedOutboxEvents).hasSize(1);

        OutBoxEvent failedOutboxEvent = failedOutboxEvents.getFirst();
        assertThat(failedOutboxEvent.aggregateId()).isEqualTo(tenantId);
        assertThat(failedOutboxEvent.correlationId()).isEqualTo(correlationId);

        JsonNode payload = objectMapper.readTree(failedOutboxEvent.payload());
        assertThat(payload.get("failureCode").asText()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED.name());
        assertThat(payload.get("retryable").asText()).isEqualTo("false");

        // === 断言 3：没有成功事件被写入 ===
        assertThat(outboxRepository.eventsByType("TenantIamProvisionedEvent")).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 场景 4：可重试失败后恢复（幂等重试）
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("幂等恢复：可重试失败后重新触发，应成功完成 Provisioning 并发布成功事件")
    void handle_shouldRecoverIdempotently_afterRetryableFailure() {
        String tenantId = "tenant-delta";
        String email = "admin@delta.com";
        String correlationId = UUID.randomUUID().toString();

        fakeKeycloak.scheduleFailures(
                KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                1,
                new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, TenantId.of(tenantId), null)
        );

        TenantInfrastructureProvisionedEventDto firstDto = new TenantInfrastructureProvisionedEventDto(
                tenantId, "Delta Corp", "BASIC", email, correlationId, Instant.now().toString()
        );

        assertThatThrownBy(() -> handler.handle(firstDto))
                .isInstanceOf(IamProvisioningException.class);

        // 第一次失败后 Outbox 里有一条失败事件
        assertThat(outboxRepository.eventsByType("TenantIamProvisioningFailedEvent")).hasSize(1);

        // 第二次触发：故障已消除，correlationId 相同（幂等重试）
        TenantInfrastructureProvisionedEventDto retryDto = new TenantInfrastructureProvisionedEventDto(
                tenantId, "Delta Corp", "BASIC", email, correlationId, Instant.now().toString()
        );

        handler.handle(retryDto);

        // === 断言 1：最终状态 COMPLETED ===
        TenantIamProvisioningState state = stateRepository.findByTenantId(TenantId.of(tenantId)).orElseThrow();
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);

        // === 断言 2：成功事件已写入 Outbox ===
        List<OutBoxEvent> successOutboxEvents = outboxRepository.eventsByType("TenantIamProvisionedEvent");
        assertThat(successOutboxEvents).hasSize(1);

        // === 断言 3：Keycloak 中只有一份数据（幂等性：前两个步骤未重复创建）===
        assertThat(fakeKeycloak.organizationsSnapshot()).hasSize(1);
        assertThat(fakeKeycloak.usersSnapshot()).hasSize(1);
    }
}