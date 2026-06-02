package io.datahub.platform.iamprovisioning.application.port.out.repository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OutBoxEventTest {

    @Test
    void should_createPendingOutboxEvent_when_requiredFieldsAreValid() {
        Instant occurredAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant createdAt = Instant.parse("2026-06-01T00:00:01Z");

        OutBoxEvent event = OutBoxEvent.pending(
                "TenantIamProvisioningState",
                "tenant-abc",
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{\"tenantId\":\"tenant-abc\"}",
                "{\"eventType\":\"TenantIamProvisionedEvent\"}",
                "corr-123",
                "infra-event-456",
                occurredAt,
                createdAt
        );

        assertThat(event.eventId()).isNull();
        assertThat(event.aggregateType()).isEqualTo("TenantIamProvisioningState");
        assertThat(event.aggregateId()).isEqualTo("tenant-abc");
        assertThat(event.eventType()).isEqualTo("TenantIamProvisionedEvent");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.topic()).isEqualTo("cdp.iam.tenant.provisioned");
        assertThat(event.payload()).isEqualTo("{\"tenantId\":\"tenant-abc\"}");
        assertThat(event.headers()).isEqualTo("{\"eventType\":\"TenantIamProvisionedEvent\"}");
        assertThat(event.status()).isEqualTo(OutBoxEvent.Status.PENDING);
        assertThat(event.retryCount()).isZero();
        assertThat(event.nextRetryAt()).isNull();
        assertThat(event.lastError()).isNull();
        assertThat(event.correlationId()).isEqualTo("corr-123");
        assertThat(event.causationId()).isEqualTo("infra-event-456");
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.createdAt()).isEqualTo(createdAt);
        assertThat(event.publishedAt()).isNull();
        assertThat(event.claimedBy()).isNull();
        assertThat(event.claimedAt()).isNull();
    }

    @Test
    void should_markPublished_when_kafkaAckIsReceived() {
        OutBoxEvent event = persistedPendingEvent();
        Instant publishedAt = Instant.parse("2026-06-01T00:00:02Z");

        OutBoxEvent published = event.markPublished(publishedAt);

        assertThat(published.eventId()).isEqualTo(event.eventId());
        assertThat(published.status()).isEqualTo(OutBoxEvent.Status.PUBLISHED);
        assertThat(published.publishedAt()).isEqualTo(publishedAt);
        assertThat(published.nextRetryAt()).isNull();
        assertThat(published.lastError()).isNull();
        assertThat(published.claimedBy()).isNull();
        assertThat(published.claimedAt()).isNull();
    }

    @Test
    void should_markClaiming_when_publisherClaimsEvent() {
        OutBoxEvent event = persistedPendingEvent();
        Instant claimedAt = Instant.parse("2026-06-01T00:00:02Z");

        OutBoxEvent claiming = event.markClaiming("publisher-1", claimedAt);

        assertThat(claiming.status()).isEqualTo(OutBoxEvent.Status.CLAIMING);
        assertThat(claiming.claimedBy()).isEqualTo("publisher-1");
        assertThat(claiming.claimedAt()).isEqualTo(claimedAt);
        assertThat(claiming.publishedAt()).isNull();
    }

    @Test
    void should_markFailed_when_publishCannotBeRetriedAnymore() {
        OutBoxEvent event = persistedPendingEvent();

        OutBoxEvent failed = event.markFailed("broker unavailable");

        assertThat(failed.status()).isEqualTo(OutBoxEvent.Status.FAILED);
        assertThat(failed.retryCount()).isEqualTo(1);
        assertThat(failed.lastError()).isEqualTo("broker unavailable");
        assertThat(failed.nextRetryAt()).isNull();
        assertThat(failed.publishedAt()).isNull();
        assertThat(failed.claimedBy()).isNull();
        assertThat(failed.claimedAt()).isNull();
    }

    @Test
    void should_scheduleRetryAndIncrementRetryCount_when_publishCanBeRetried() {
        OutBoxEvent event = persistedPendingEvent();
        Instant nextRetryAt = Instant.parse("2026-06-01T00:01:00Z");

        OutBoxEvent retry = event.scheduleRetry("temporary broker failure", nextRetryAt);

        assertThat(retry.status()).isEqualTo(OutBoxEvent.Status.PENDING);
        assertThat(retry.retryCount()).isEqualTo(1);
        assertThat(retry.lastError()).isEqualTo("temporary broker failure");
        assertThat(retry.nextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(retry.publishedAt()).isNull();
        assertThat(retry.claimedBy()).isNull();
        assertThat(retry.claimedAt()).isNull();
    }

    @Test
    void should_rejectOutboxEvent_when_requiredStringFieldIsBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> OutBoxEvent.pending(
                        " ",
                        "tenant-abc",
                        "TenantIamProvisionedEvent",
                        1,
                        "cdp.iam.tenant.provisioned",
                        "{}",
                        "{}",
                        "corr-123",
                        null,
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-06-01T00:00:01Z")
                ))
                .withMessageContaining("aggregateType");
    }

    @Test
    void should_rehydratePersistedOutboxEvent_withExistingIdentifier() {
        UUID eventId = UUID.fromString("3c9ebf9e-2fc4-4978-b4cf-3577b8b998db");

        OutBoxEvent event = OutBoxEvent.rehydrate(
                eventId,
                "TenantIamProvisioningState",
                "tenant-abc",
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{}",
                "{}",
                OutBoxEvent.Status.PENDING,
                2,
                Instant.parse("2026-06-01T00:01:00Z"),
                "temporary broker failure",
                "corr-123",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:01Z"),
                null,
                null,
                null
        );

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.retryCount()).isEqualTo(2);
        assertThat(event.lastError()).isEqualTo("temporary broker failure");
    }

    @Test
    void should_rejectPersistedStateChange_when_eventHasNotBeenInsertedYet() {
        OutBoxEvent event = pendingEvent();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> event.markPublished(Instant.parse("2026-06-01T00:00:02Z")))
                .withMessageContaining("eventId");
    }

    private static OutBoxEvent pendingEvent() {
        return OutBoxEvent.pending(
                "TenantIamProvisioningState",
                "tenant-abc",
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{}",
                "{}",
                "corr-123",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:01Z")
        );
    }

    private static OutBoxEvent persistedPendingEvent() {
        return OutBoxEvent.rehydrate(
                UUID.fromString("3c9ebf9e-2fc4-4978-b4cf-3577b8b998db"),
                "TenantIamProvisioningState",
                "tenant-abc",
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{}",
                "{}",
                OutBoxEvent.Status.PENDING,
                0,
                null,
                null,
                "corr-123",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:01Z"),
                null,
                null,
                null
        );
    }
}
