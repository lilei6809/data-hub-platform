package io.datahub.platform.iamprovisioning.application.port.out.repository;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 这是一个 Port 接口，它说的是"业务需要什么能力"
// 注意：零基础设施依赖，没有 @Repository，没有 JPA 类型
public interface TenantIamProvisioningStateRepository {

    // 场景一（Kafka Consumer / Admin Retry API 触发时）：
    // 加载或初始化。这是 Application Service 进入每次执行时的第一步。
    // 语义：存在则返回已有状态（可能是 FAILED 等待重试），不存在则创建 PENDING 并保存。
    TenantIamProvisioningState findOrInitById(TenantId tenantId, CorrelationId correlationId);

    // 场景二（每个 checkpoint 后）：
    // 保存最新状态。频繁调用，必须是覆盖写（幂等）。
    // 同一个 tenantId 多次调用只保留最新版本，不产生重复记录。
    void save(TenantIamProvisioningState tenantIamProvisioningState);

    // 场景三（RetryScheduler 轮询时）：
    // 找出所有"等待重试且时间已到"的记录。
    // limit 参数防止一次性捞出太多记录压垮系统。
    // 生产实现需要用 FOR UPDATE SKIP LOCKED 防止多实例重复处理。
    List<TenantIamProvisioningState> findReadyForRetry(Instant now, int limit);


    // 可选（Admin 管理 API 使用，如查询某个租户当前状态）：
    Optional<TenantIamProvisioningState> findByTenantId(TenantId tenantId);


    // 场景:  retryScheduler 需要声明一批可重试记录的认领权
    List<TenantIamProvisioningState> claimBatch(int limit, String claimedBy);

    void claim(String tenantId, String claimedBy, Instant timestamp);

    int reclaimStale(Duration staleThreshold);
}
