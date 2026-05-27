package io.datahub.platform.iamprovisioning.application.port.out;

import io.datahub.platform.iamprovisioning.application.exception.TenantIamProvisioningStateConcurrencyException;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public abstract class TenantIamProvisioningStateRepositoryContractTest {

    protected abstract TenantIamProvisioningStateRepository createRepository();

    private TenantIamProvisioningStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = createRepository();
    }

    @Test
    @DisplayName("findOrInitById: 不存在时创建 PENDING 状态")
    void shouldCreatePendingState_whenFindOrInitByIdAndStateDoesNotExist() {
        TenantId tenantId = TenantId.of("tenant-abc");
        CorrelationId correlationId = CorrelationId.of("correlation-id");

        TenantIamProvisioningState state = repository.findOrInitById(tenantId, correlationId);

        assertThat(state).isNotNull();
        assertThat(state.getTenantId()).isEqualTo(tenantId);
        assertThat(state.getWorkflowCorrelationId()).isEqualTo(correlationId);
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.PENDING);
        assertThat(state.getVersion()).isZero();
    }

    @Test
    @DisplayName("findOrInitById: 已存在时返回已有状态，不重新初始化")
    void shouldReturnExistingState_whenFindOrInitByIdAndStateAlreadyExists() {
        TenantId tenantId = TenantId.of("tenant-abc");
        CorrelationId firstCorrelationId = CorrelationId.of("correlation-id-1");
        CorrelationId secondCorrelationId = CorrelationId.of("correlation-id-2");
        TenantIamProvisioningState existing = repository.findOrInitById(tenantId, firstCorrelationId);
        existing.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));
        repository.save(existing);

        TenantIamProvisioningState loaded = repository.findOrInitById(tenantId, secondCorrelationId);

        assertThat(loaded.getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.getWorkflowCorrelationId()).isEqualTo(firstCorrelationId);
        assertThat(loaded.getOverallStatus()).isEqualTo(IamProvisioningStatus.IN_PROGRESS);
        assertThat(loaded.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("save/findByTenantId: 保存后可以按 tenantId 读取")
    void shouldPersistState_whenSave() {
        TenantId tenantId = TenantId.of("tenant-save");
        TenantIamProvisioningState state = newState(tenantId, "corr-save");
        state.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));

        repository.save(state);

        Optional<TenantIamProvisioningState> loaded = repository.findByTenantId(tenantId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.get().getOverallStatus()).isEqualTo(IamProvisioningStatus.IN_PROGRESS);
        assertThat(loaded.get().getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByTenantId: 不存在时返回 empty")
    void shouldReturnEmpty_whenFindByTenantIdAndStateDoesNotExist() {
        assertThat(repository.findByTenantId(TenantId.of("tenant-missing"))).isEmpty();
    }

    @Test
    @DisplayName("repository: 读取结果应是快照，不能直接修改已持久化对象")
    void shouldReturnSnapshot_whenStateIsLoaded() {
        TenantId tenantId = TenantId.of("tenant-snapshot");
        TenantIamProvisioningState firstLoaded = repository.findOrInitById(tenantId, CorrelationId.of("corr-snapshot"));

        firstLoaded.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));

        TenantIamProvisioningState secondLoaded = repository.findOrInitById(tenantId, CorrelationId.of("ignored-corr"));
        assertThat(secondLoaded.getOverallStatus()).isEqualTo(IamProvisioningStatus.PENDING);
    }

    @Test
    @DisplayName("findReadyForRetry: 只返回等待重试且时间已到的状态")
    void shouldFindOnlyDueAwaitingRetryStates_whenFindReadyForRetry() {
        Instant now = Instant.parse("2026-05-26T00:00:00Z");
        TenantIamProvisioningState dueRetry = awaitingRetryState(
                TenantId.of("tenant-due"),
                "corr-due",
                Instant.parse("2026-05-25T00:00:00Z")
        );
        TenantIamProvisioningState futureRetry = awaitingRetryState(
                TenantId.of("tenant-future"),
                "corr-future",
                now
        );
        TenantIamProvisioningState failed = failedState(TenantId.of("tenant-failed"), "corr-failed");

        repository.save(dueRetry);
        repository.save(futureRetry);
        repository.save(failed);

        List<TenantIamProvisioningState> readyStates = repository.findReadyForRetry(now, 10);

        assertThat(readyStates)
                .extracting(TenantIamProvisioningState::getTenantId)
                .containsExactly(TenantId.of("tenant-due"));
    }

    @Test
    @DisplayName("findReadyForRetry: limit 应限制返回数量")
    void shouldLimitReadyRetryResults_whenLimitIsProvided() {
        Instant now = Instant.parse("2026-05-26T00:00:00Z");
        repository.save(awaitingRetryState(TenantId.of("tenant-due-1"), "corr-due-1", Instant.parse("2026-05-25T00:00:00Z")));
        repository.save(awaitingRetryState(TenantId.of("tenant-due-2"), "corr-due-2", Instant.parse("2026-05-25T00:00:00Z")));

        List<TenantIamProvisioningState> readyStates = repository.findReadyForRetry(now, 1);

        assertThat(readyStates).hasSize(1);
    }

    @Test
    @DisplayName("save: 旧版本快照再次保存时应抛出并发冲突异常")
    void shouldThrowConcurrencyException_whenSavingStaleSnapshot() {
        TenantId tenantId = TenantId.of("tenant-concurrent");
        TenantIamProvisioningState firstSnapshot = repository.findOrInitById(tenantId, CorrelationId.of("corr-concurrent"));
        TenantIamProvisioningState secondSnapshot = repository.findOrInitById(tenantId, CorrelationId.of("corr-concurrent"));

        firstSnapshot.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));
        repository.save(firstSnapshot);

        secondSnapshot.markInProgress(Instant.parse("2026-05-25T00:00:02Z"));
        assertThatExceptionOfType(TenantIamProvisioningStateConcurrencyException.class)
                .isThrownBy(() -> repository.save(secondSnapshot))
                .withMessageContaining("tenant-concurrent");
    }

    private TenantIamProvisioningState newState(TenantId tenantId, String correlationId) {
        return TenantIamProvisioningState.init(
                tenantId,
                CorrelationId.of(correlationId),
                Instant.parse("2026-05-25T00:00:00Z")
        );
    }

    private TenantIamProvisioningState awaitingRetryState(TenantId tenantId, String correlationId, Instant failedAt) {
        TenantIamProvisioningState state = newState(tenantId, correlationId);
        state.markInProgress(failedAt.minusSeconds(1));
        state.markAwaitRetry(failedAt, IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE, "keycloak unavailable");
        return state;
    }

    private TenantIamProvisioningState failedState(TenantId tenantId, String correlationId) {
        TenantIamProvisioningState state = newState(tenantId, correlationId);
        state.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));
        state.markFailed(
                Instant.parse("2026-05-25T00:00:02Z"),
                IamProvisioningFailureCode.UNKNOWN_ERROR,
                "terminal failure"
        );
        return state;
    }

}
