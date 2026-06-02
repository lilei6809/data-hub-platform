package io.datahub.platform.iamprovisioning.application.port.out.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void appendAll(List<OutBoxEvent> events);

    List<OutBoxEvent> findPendingForPublish(int limit);

    void markPublished(UUID eventId, Instant timestamp);

    void scheduleRetry(UUID eventId, String lastError, Instant nextRetryAt);

    void markFailed(UUID eventId, String lastError);
}
