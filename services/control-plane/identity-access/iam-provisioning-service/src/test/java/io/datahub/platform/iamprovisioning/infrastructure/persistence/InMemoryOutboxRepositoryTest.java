package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOutboxRepositoryTest {

    private final InMemoryOutboxRepository repository = new InMemoryOutboxRepository();

    @Test
    void claimBatch_marksReadyPendingEventsAsClaiming() {
        repository.appendAll(List.of(pendingEvent("tenant-alpha"), pendingEvent("tenant-beta")));

        List<OutBoxEvent> claimed = repository.claimBatch(1, "publisher-1");

        assertThat(claimed).hasSize(1);
        OutBoxEvent claimedEvent = claimed.getFirst();
        assertThat(claimedEvent.status()).isEqualTo(OutBoxEvent.Status.CLAIMING);
        assertThat(claimedEvent.claimedBy()).isEqualTo("publisher-1");
        assertThat(claimedEvent.claimedAt()).isNotNull();

        assertThat(repository.allEvents())
                .filteredOn(event -> event.eventId().equals(claimedEvent.eventId()))
                .singleElement()
                .extracting(OutBoxEvent::status)
                .isEqualTo(OutBoxEvent.Status.CLAIMING);
    }

    @Test
    void claimBatch_excludesAlreadyClaimedEventsAndFutureRetries() {
        repository.appendAll(List.of(pendingEvent("tenant-alpha"), pendingEvent("tenant-beta")));
        UUID futureRetryEventId = repository.allEvents().getLast().eventId();
        repository.scheduleRetry(
                futureRetryEventId,
                "temporary broker failure",
                Instant.now().plus(Duration.ofMinutes(5))
        );

        OutBoxEvent firstClaimed = repository.claimBatch(1, "publisher-1").getFirst();
        List<OutBoxEvent> secondClaim = repository.claimBatch(10, "publisher-2");

        assertThat(secondClaim).isEmpty();
        assertThat(repository.allEvents())
                .filteredOn(event -> event.eventId().equals(firstClaimed.eventId()))
                .singleElement()
                .extracting(OutBoxEvent::claimedBy)
                .isEqualTo("publisher-1");
    }

    @Test
    void reclaimStale_movesExpiredClaimingEventsBackToPending() {
        repository.appendAll(List.of(pendingEvent("tenant-alpha")));
        OutBoxEvent claimed = repository.claimBatch(1, "publisher-1").getFirst();

        int reclaimed = repository.reclaimStale(Duration.ZERO);

        assertThat(reclaimed).isEqualTo(1);
        assertThat(repository.allEvents())
                .filteredOn(event -> event.eventId().equals(claimed.eventId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(OutBoxEvent.Status.PENDING);
                    assertThat(event.claimedBy()).isNull();
                    assertThat(event.claimedAt()).isNull();
                });
    }

    private static OutBoxEvent pendingEvent(String tenantId) {
        return OutBoxEvent.pending(
                "TenantIamProvisioningState",
                tenantId,
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{\"tenantId\":\"" + tenantId + "\"}",
                "{}",
                "corr-123",
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:01Z")
        );
    }
}
