package io.datahub.platform.iamprovisioning.infrastructure.persistence;
import io.datahub.platform.iamprovisioning.application.exception.TenantIamProvisioningStateConcurrencyException;
import io.datahub.platform.iamprovisioning.application.port.out.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTenantIamProvisioningStateRepository implements TenantIamProvisioningStateRepository {

    // ConcurrentHashMap 只保证单个 key 的 compute 原子执行；
    // 乐观锁语义仍然依赖 version 检查和快照隔离。
    private final ConcurrentHashMap<TenantId, TenantIamProvisioningState> store = new ConcurrentHashMap<>();

    @Override
    public TenantIamProvisioningState findOrInitById(TenantId tenantId, CorrelationId correlationId) {
        // computeIfAbsent 是原子操作：查到了就返回，查不到就计算并放入
        // 这比 "先 get 再判断 null 再 put" 更安全，避免竞态条件
        TenantIamProvisioningState stored =
                store.computeIfAbsent(tenantId,
                        id -> TenantIamProvisioningState.init(tenantId, correlationId, Instant.now()));

        // 返回快照, 而不是同一份内存对象
        return stored.snapshot();
    }


    /**
     *
     * @param newState newState是内存对象快照的更新
     */
    @Override
    public void save(TenantIamProvisioningState newState) {
        // compute() 的整个 lambda 执行是对这个 key 原子的
        // 在 lambda 执行期间，其他线程对同一个 key 的 compute 会等待
        store.compute(newState.getTenantId(), (id, existing) -> {
           if (existing != null
           && existing.getVersion() != newState.getVersion()) {
               throw new TenantIamProvisioningStateConcurrencyException(
                       newState.getTenantId(),
                       existing.getVersion(),
                       newState.getVersion()
               );
           }

           TenantIamProvisioningState persisted = newState.snapshot();
           persisted.markPersisted(newState.getVersion() + 1);
           newState.markPersisted(persisted.getVersion());
           return persisted;
        });
    }

    @Override
    public List<TenantIamProvisioningState> findReadyForRetry(Instant now, int limit) {

        return store.values().stream()
                .filter(state -> state.getOverallStatus() == IamProvisioningStatus.AWAITING_RETRY)
                // 不需要判断是否重试耗尽, 因为 AWAITING_RETRY 就表示可以重试
                .filter(state -> state.getNextRetryAt() != null)
                .filter(state -> !state.getNextRetryAt().isAfter(now))
                .limit(Math.max(limit, 0))
                .map(TenantIamProvisioningState::snapshot)
                .toList();

    }

    @Override
    public Optional<TenantIamProvisioningState> findByTenantId(TenantId tenantId) {
        return Optional.ofNullable(store.get(tenantId))
                .map(TenantIamProvisioningState::snapshot);
    }
}
