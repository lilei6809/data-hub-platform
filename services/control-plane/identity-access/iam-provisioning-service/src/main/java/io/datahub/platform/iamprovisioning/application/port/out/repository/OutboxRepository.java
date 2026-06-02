package io.datahub.platform.iamprovisioning.application.port.out.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void appendAll(List<OutBoxEvent> events);

//    List<OutBoxEvent> findPendingForPublish(int limit);

    List<OutBoxEvent> claimBatch(int limit, String claimedBy);

    /**
     * 回收超时的 CLAIMING 事件（崩溃实例遗留）。
     * 返回回收的事件数量。
     */
    int reclaimStale(java.time.Duration staleThreshold);

    void markPublished(UUID eventId, Instant timestamp);

    void scheduleRetry(UUID eventId, String lastError, Instant nextRetryAt);

    void markFailed(UUID eventId, String lastError);
}
