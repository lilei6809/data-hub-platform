package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRow(
        String eventId,
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
        Instant publishedAt
) {
}
