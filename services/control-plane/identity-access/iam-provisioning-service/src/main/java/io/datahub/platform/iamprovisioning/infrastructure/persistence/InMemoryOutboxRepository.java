package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class InMemoryOutboxRepository implements OutboxRepository {

    private final Map<UUID, OutBoxEvent> outboxEvents = new LinkedHashMap<>();

    @Override
    public void appendAll(List<OutBoxEvent> events) {
        events.forEach(outboxEvent -> {
            UUID uuid = UUID.randomUUID();

            outboxEvents.put(uuid, outboxEvent.withId(uuid));
        });
    }

    @Override
    public List<OutBoxEvent> claimBatch(int limit, String claimedBy) {
        if (limit <= 0) {
            return List.of();
        }

        Instant claimedAt = Instant.now();
        List<OutBoxEvent> claimedEvents = outboxEvents.values()
                .stream()
                .filter(event -> event.status() == OutBoxEvent.Status.PENDING)
                .filter(event -> event.nextRetryAt() == null || !event.nextRetryAt().isAfter(claimedAt))
                .limit(limit)
                .map(event -> event.markClaiming(claimedBy, claimedAt))
                .toList();

        claimedEvents.forEach(event -> replace(event.eventId(), event));

        return claimedEvents;
    }

    @Override
    public int reclaimStale(Duration staleThreshold) {
        Objects.requireNonNull(staleThreshold, "staleThreshold must not be null");

        Instant staleBefore = Instant.now().minus(staleThreshold);
        List<OutBoxEvent> staleEvents = outboxEvents.values()
                .stream()
                .filter(event -> event.status() == OutBoxEvent.Status.CLAIMING)
                .filter(event -> event.claimedAt() != null && event.claimedAt().isBefore(staleBefore))
                .map(this::releaseClaim)
                .toList();

        staleEvents.forEach(event -> replace(event.eventId(), event));

        return staleEvents.size();
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

    private OutBoxEvent releaseClaim(OutBoxEvent event) {
        return OutBoxEvent.rehydrate(
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.eventVersion(),
                event.topic(),
                event.payload(),
                event.headers(),
                OutBoxEvent.Status.PENDING,
                event.retryCount(),
                event.nextRetryAt(),
                event.lastError(),
                event.correlationId(),
                event.causationId(),
                event.occurredAt(),
                event.createdAt(),
                null,
                null,
                null
        );
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
