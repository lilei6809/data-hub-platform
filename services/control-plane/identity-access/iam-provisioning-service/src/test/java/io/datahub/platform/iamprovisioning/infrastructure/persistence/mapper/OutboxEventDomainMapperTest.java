package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.OutboxEventRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventDomainMapperTest {

    private final OutboxEventDomainMapper mapper = new OutboxEventDomainMapper();

    @Test
    void should_mapPersistedDomainEventToRow() {
        OutBoxEvent event = persistedEvent();

        OutboxEventRow row = mapper.toRow(event);

        assertThat(row.eventId()).isEqualTo(event.eventId().toString());
        assertThat(row.aggregateType()).isEqualTo(event.aggregateType());
        assertThat(row.aggregateId()).isEqualTo(event.aggregateId());
        assertThat(row.eventType()).isEqualTo(event.eventType());
        assertThat(row.eventVersion()).isEqualTo(event.eventVersion());
        assertThat(row.topic()).isEqualTo(event.topic());
        assertThat(row.payload()).isEqualTo(event.payload());
        assertThat(row.headers()).isEqualTo(event.headers());
        assertThat(row.status()).isEqualTo(event.status().name());
        assertThat(row.retryCount()).isEqualTo(event.retryCount());
        assertThat(row.nextRetryAt()).isEqualTo(event.nextRetryAt());
        assertThat(row.lastError()).isEqualTo(event.lastError());
        assertThat(row.correlationId()).isEqualTo(event.correlationId());
        assertThat(row.causationId()).isEqualTo(event.causationId());
        assertThat(row.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(row.createdAt()).isEqualTo(event.createdAt());
        assertThat(row.publishedAt()).isEqualTo(event.publishedAt());
        assertThat(row.claimedBy()).isEqualTo(event.claimedBy());
        assertThat(row.claimedAt()).isEqualTo(event.claimedAt());
    }

    @Test
    void should_rehydrateDomainEventFromRow() {
        UUID eventId = UUID.fromString("3c9ebf9e-2fc4-4978-b4cf-3577b8b998db");
        OutboxEventRow row = new OutboxEventRow(
                eventId.toString(),
                "TenantIamProvisioningState",
                "tenant-abc",
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{\"tenantId\":\"tenant-abc\"}",
                "{\"eventType\":\"TenantIamProvisionedEvent\"}",
                "PENDING",
                2,
                Instant.parse("2026-06-01T00:01:00Z"),
                "temporary broker failure",
                "corr-123",
                "infra-event-456",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:01Z"),
                null,
                "publisher-1",
                null
        );

        OutBoxEvent event = mapper.toDomain(row);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.aggregateType()).isEqualTo(row.aggregateType());
        assertThat(event.aggregateId()).isEqualTo(row.aggregateId());
        assertThat(event.eventType()).isEqualTo(row.eventType());
        assertThat(event.eventVersion()).isEqualTo(row.eventVersion());
        assertThat(event.topic()).isEqualTo(row.topic());
        assertThat(event.payload()).isEqualTo(row.payload());
        assertThat(event.headers()).isEqualTo(row.headers());
        assertThat(event.status()).isEqualTo(OutBoxEvent.Status.PENDING);
        assertThat(event.retryCount()).isEqualTo(row.retryCount());
        assertThat(event.nextRetryAt()).isEqualTo(row.nextRetryAt());
        assertThat(event.lastError()).isEqualTo(row.lastError());
        assertThat(event.correlationId()).isEqualTo(row.correlationId());
        assertThat(event.causationId()).isEqualTo(row.causationId());
        assertThat(event.occurredAt()).isEqualTo(row.occurredAt());
        assertThat(event.createdAt()).isEqualTo(row.createdAt());
        assertThat(event.publishedAt()).isNull();
        assertThat(event.claimedBy()).isEqualTo(row.claimedBy());
        assertThat(event.claimedAt()).isNull();
    }

    private static OutBoxEvent persistedEvent() {
        return OutBoxEvent.rehydrate(
                UUID.fromString("3c9ebf9e-2fc4-4978-b4cf-3577b8b998db"),
                "TenantIamProvisioningState",
                "tenant-abc",
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{\"tenantId\":\"tenant-abc\"}",
                "{\"eventType\":\"TenantIamProvisionedEvent\"}",
                OutBoxEvent.Status.PENDING,
                2,
                Instant.parse("2026-06-01T00:01:00Z"),
                "temporary broker failure",
                "corr-123",
                "infra-event-456",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:01Z"),
                null,
                null,
                null
        );
    }
}
