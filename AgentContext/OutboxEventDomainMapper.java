package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.OutboxEventRow;

import java.util.UUID;

public class OutboxEventDomainMapper {

    public OutBoxEvent toDomain(OutboxEventRow row){
        return OutBoxEvent.rehydrate(
                UUID.fromString(row.eventId()),
                row.aggregateType(),
                row.aggregateId(),
                row.eventType(),
                row.eventVersion(),
                row.topic(),
                row.payload(),
                row.headers(),
                OutBoxEvent.Status.valueOf(row.status()),
                row.retryCount(),
                row.nextRetryAt(),
                row.lastError(),
                row.correlationId(),
                row.causationId(),
                row.occurredAt(),
                row.createdAt(),
                row.publishedAt()
        );
    }

    public OutboxEventRow toRow(OutBoxEvent event){
        return new OutboxEventRow(
                event.eventId() == null ? null : event.eventId().toString(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.eventVersion(),
                event.topic(),
                event.payload(),
                event.headers(),
                event.status().name(),
                event.retryCount(),
                event.nextRetryAt(),
                event.lastError(),
                event.correlationId(),
                event.causationId(),
                event.occurredAt(),
                event.createdAt(),
                event.publishedAt()
        );
    }
}
