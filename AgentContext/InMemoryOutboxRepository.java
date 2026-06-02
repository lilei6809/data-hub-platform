package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;

import java.time.Instant;
import java.util.*;

public class InMemoryOutboxRepository implements OutboxRepository {

    private final Map<UUID, OutBoxEvent> outboxEvents = new LinkedHashMap<>();

    @Override
    public void appendAll(List<OutBoxEvent> events) {
        events.forEach(outboxEvent -> {
            UUID uuid = UUID.randomUUID();

            outboxEvents.put(uuid, outboxEvent.withId(uuid));
        });
    }

    // 暂时不考虑 nextRetryAt 条件
    @Override
    public List<OutBoxEvent> findPendingForPublish(int limit) {
        return outboxEvents.values()
                .stream()
                .filter(event -> event.status().equals(OutBoxEvent.Status.PENDING))
                .limit(limit)
                .toList();
    }

    @Override
    public void markPublished(UUID eventId, Instant timestamp) {
        replace(eventId, outboxEvents.get(eventId).markPublished(timestamp));
    }

    private void replace(UUID eventId, OutBoxEvent outBoxEvent) {
        outboxEvents.put(eventId, outBoxEvent);
    }

    @Override
    public void scheduleRetry(UUID eventId, String lastError, Instant nextRetryAt) {
        replace(eventId, outboxEvents.get(eventId).scheduleRetry(lastError, nextRetryAt));
    }

    @Override
    public void markFailed(UUID eventId, String lastError) {
        replace(eventId, outboxEvents.get(eventId).markFailed(lastError));
    }

    public List<OutBoxEvent> allEvents() {
        return outboxEvents.values().stream().toList();
    }

    public List<OutBoxEvent> eventsByType(String eventType) {
        return outboxEvents.values().stream()
                .filter(event -> event.eventType().equals(eventType))
                .toList();
    }

}
