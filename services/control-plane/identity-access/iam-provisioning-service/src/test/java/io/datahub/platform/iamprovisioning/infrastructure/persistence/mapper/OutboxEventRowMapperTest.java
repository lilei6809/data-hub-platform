package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.OutboxEventRow;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxEventRowMapperTest {

    private final OutboxEventRowMapper mapper = new OutboxEventRowMapper();

    @Test
    void should_mapResultSetToOutboxEventRow() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        UUID eventId = UUID.fromString("3c9ebf9e-2fc4-4978-b4cf-3577b8b998db");
        Instant nextRetryAt = Instant.parse("2026-06-01T00:01:00Z");
        Instant occurredAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant createdAt = Instant.parse("2026-06-01T00:00:01Z");
        Instant publishedAt = Instant.parse("2026-06-01T00:00:02Z");
        Instant claimedAt = Instant.parse("2026-06-01T00:00:03Z");

        when(resultSet.getObject("event_id", UUID.class)).thenReturn(eventId);
        when(resultSet.getString("aggregate_type")).thenReturn("TenantIamProvisioningState");
        when(resultSet.getString("aggregate_id")).thenReturn("tenant-abc");
        when(resultSet.getString("event_type")).thenReturn("TenantIamProvisionedEvent");
        when(resultSet.getInt("event_version")).thenReturn(1);
        when(resultSet.getString("topic")).thenReturn("cdp.iam.tenant.provisioned");
        when(resultSet.getString("payload")).thenReturn("{\"tenantId\":\"tenant-abc\"}");
        when(resultSet.getString("headers")).thenReturn("{\"eventType\":\"TenantIamProvisionedEvent\"}");
        when(resultSet.getString("status")).thenReturn("PUBLISHED");
        when(resultSet.getInt("retry_count")).thenReturn(2);
        when(resultSet.getTimestamp("next_retry_at")).thenReturn(Timestamp.from(nextRetryAt));
        when(resultSet.getString("last_error")).thenReturn("temporary broker failure");
        when(resultSet.getString("correlation_id")).thenReturn("corr-123");
        when(resultSet.getString("causation_id")).thenReturn("infra-event-456");
        when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(occurredAt));
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
        when(resultSet.getTimestamp("published_at")).thenReturn(Timestamp.from(publishedAt));
        when(resultSet.getString("claimed_by")).thenReturn("publisher-1");
        when(resultSet.getTimestamp("claimed_at")).thenReturn(Timestamp.from(claimedAt));

        OutboxEventRow row = mapper.mapRow(resultSet, 0);

        assertThat(row.eventId()).isEqualTo(eventId);
        assertThat(row.aggregateType()).isEqualTo("TenantIamProvisioningState");
        assertThat(row.aggregateId()).isEqualTo("tenant-abc");
        assertThat(row.eventType()).isEqualTo("TenantIamProvisionedEvent");
        assertThat(row.eventVersion()).isEqualTo(1);
        assertThat(row.topic()).isEqualTo("cdp.iam.tenant.provisioned");
        assertThat(row.payload()).isEqualTo("{\"tenantId\":\"tenant-abc\"}");
        assertThat(row.headers()).isEqualTo("{\"eventType\":\"TenantIamProvisionedEvent\"}");
        assertThat(row.status()).isEqualTo("PUBLISHED");
        assertThat(row.retryCount()).isEqualTo(2);
        assertThat(row.nextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(row.lastError()).isEqualTo("temporary broker failure");
        assertThat(row.correlationId()).isEqualTo("corr-123");
        assertThat(row.causationId()).isEqualTo("infra-event-456");
        assertThat(row.occurredAt()).isEqualTo(occurredAt);
        assertThat(row.createdAt()).isEqualTo(createdAt);
        assertThat(row.publishedAt()).isEqualTo(publishedAt);
        assertThat(row.claimedBy()).isEqualTo("publisher-1");
        assertThat(row.claimedAt()).isEqualTo(claimedAt);
    }

    @Test
    void should_mapNullableTimestampsToNull() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getObject("event_id", UUID.class))
                .thenReturn(UUID.fromString("3c9ebf9e-2fc4-4978-b4cf-3577b8b998db"));
        when(resultSet.getString("aggregate_type")).thenReturn("TenantIamProvisioningState");
        when(resultSet.getString("aggregate_id")).thenReturn("tenant-abc");
        when(resultSet.getString("event_type")).thenReturn("TenantIamProvisionedEvent");
        when(resultSet.getInt("event_version")).thenReturn(1);
        when(resultSet.getString("topic")).thenReturn("cdp.iam.tenant.provisioned");
        when(resultSet.getString("payload")).thenReturn("{}");
        when(resultSet.getString("headers")).thenReturn("{}");
        when(resultSet.getString("status")).thenReturn("PENDING");
        when(resultSet.getInt("retry_count")).thenReturn(0);
        when(resultSet.getTimestamp("next_retry_at")).thenReturn(null);
        when(resultSet.getString("last_error")).thenReturn(null);
        when(resultSet.getString("correlation_id")).thenReturn("corr-123");
        when(resultSet.getString("causation_id")).thenReturn(null);
        when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-06-01T00:00:00Z")));
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-06-01T00:00:01Z")));
        when(resultSet.getTimestamp("published_at")).thenReturn(null);
        when(resultSet.getString("claimed_by")).thenReturn(null);
        when(resultSet.getTimestamp("claimed_at")).thenReturn(null);

        OutboxEventRow row = mapper.mapRow(resultSet, 0);

        assertThat(row.nextRetryAt()).isNull();
        assertThat(row.lastError()).isNull();
        assertThat(row.causationId()).isNull();
        assertThat(row.publishedAt()).isNull();
        assertThat(row.claimedBy()).isNull();
        assertThat(row.claimedAt()).isNull();
    }
}
