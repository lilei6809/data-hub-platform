package io.datahub.platform.iamprovisioning.application.port.out.repository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutBoxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        int eventVersion,
        String topic,
        String payload,
        String headers,
        Status status,
        int retryCount,
        Instant nextRetryAt,
        String lastError,
        String correlationId,
        String causationId,
        Instant occurredAt,
        Instant createdAt,
        Instant publishedAt,
        String claimedBy,
        Instant claimedAt
) {

    public OutBoxEvent {
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        topic = requireText(topic, "topic");
        payload = requireText(payload, "payload");
        headers = requireText(headers, "headers");
        status = Objects.requireNonNull(status, "status must not be null");
        correlationId = requireText(correlationId, "correlationId");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be greater than or equal to 1");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (status != Status.PENDING && eventId == null) {
            throw new IllegalArgumentException("eventId must not be null when status is " + status);
        }
        if (status == Status.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("publishedAt must not be null when status is PUBLISHED");
        }
        if (status == Status.CLAIMING) {
            claimedBy = requireText(claimedBy, "claimedBy");
            claimedAt = Objects.requireNonNull(claimedAt, "claimedAt must not be null when status is CLAIMING");
        }
    }

    public static OutBoxEvent pending(
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            String topic,
            String payload,
            String headers,
            String correlationId,
            String causationId,
            Instant occurredAt,
            Instant createdAt
    ) {
        return new OutBoxEvent(
                null,
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                Status.PENDING,
                0,
                null,
                null,
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                null,
                null,
                null
        );
    }


    public OutBoxEvent withId(UUID eventId) {
        return rehydrate(eventId,
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                status,
                retryCount,
                nextRetryAt,
                lastError,
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                publishedAt,
                claimedBy,
                claimedAt
                );
    }

    // 数据库 row -> OutBoxEvent
    public static OutBoxEvent rehydrate(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            String topic,
            String payload,
            String headers,
            Status status,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            String correlationId,
            String causationId,
            Instant occurredAt,
            Instant createdAt,
            Instant publishedAt,
            String claimedBy,
            Instant claimedAt
    ) {
        return new OutBoxEvent(
                Objects.requireNonNull(eventId, "eventId must not be null"),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                status,
                retryCount,
                nextRetryAt,
                lastError,
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                publishedAt,
                claimedBy,
                claimedAt
        );
    }

    public OutBoxEvent markClaiming(String claimedBy, Instant claimedAt) {
        return new OutBoxEvent(
                requirePersistedEventId(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                Status.CLAIMING,
                retryCount,
                nextRetryAt,
                lastError,
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                null,
                requireText(claimedBy, "claimedBy"),
                Objects.requireNonNull(claimedAt, "claimedAt must not be null")
        );
    }

    public OutBoxEvent markPublished(Instant publishedAt) {
        return new OutBoxEvent(
                requirePersistedEventId(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                Status.PUBLISHED,
                retryCount,
                null,
                null,
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                Objects.requireNonNull(publishedAt, "publishedAt must not be null"),
                null,
                null
        );
    }

    public OutBoxEvent markFailed(String lastError) {
        return new OutBoxEvent(
                requirePersistedEventId(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                Status.FAILED,
                retryCount + 1,
                null,
                requireText(lastError, "lastError"),
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                null,
                null,
                null
        );
    }

    public OutBoxEvent scheduleRetry(String lastError, Instant nextRetryAt) {
        return new OutBoxEvent(
                requirePersistedEventId(),
                aggregateType,
                aggregateId,
                eventType,
                eventVersion,
                topic,
                payload,
                headers,
                Status.PENDING,
                retryCount + 1,
                Objects.requireNonNull(nextRetryAt, "nextRetryAt must not be null"),
                requireText(lastError, "lastError"),
                correlationId,
                causationId,
                occurredAt,
                createdAt,
                null,
                null,
                null
        );
    }

    private UUID requirePersistedEventId() {
        if (eventId == null) {
            throw new IllegalStateException("eventId must exist before changing persisted outbox state");
        }
        return eventId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public enum Status {
        PENDING,
        PUBLISHED,
        CLAIMING,
        FAILED
    }
}
