package io.datahub.platform.iamprovisioning.infrastructure.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRow(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        int eventVersion,
        String topic,
        String payload,
        String headers,
        String status,
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
}
