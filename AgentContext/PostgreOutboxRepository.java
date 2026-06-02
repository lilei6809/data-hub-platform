package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PostgreOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final OutboxEventDomainMapper domainMapper =  new OutboxEventDomainMapper();
    private final OutboxEventRowMapper rowMapper =  new OutboxEventRowMapper();

    public PostgreOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void appendAll(List<OutBoxEvent> events) {
        events.forEach(event -> {
            jdbcTemplate.update(
                    """
                        INSERT INTO outbox_events(aggregate_type, aggregate_id, event_type, event_version, 
                                                  topic, payload, headers, next_retry_at, 
                                                  last_error, correlation_id, causation_id, 
                                                  occurred_at, published_at) 
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?) 
                        """,
                    event.aggregateType(),
                    event.aggregateId(),
                    event.eventType(),
                    event.eventVersion(),
                    event.topic(),
                    event.payload(),
                    event.headers(),
                    toTimestamp(event.nextRetryAt()),
                    event.lastError(),
                    event.correlationId(),
                    event.causationId(),
                    toTimestamp(event.occurredAt()),
                    toTimestamp(event.publishedAt())
            );
        });
    }

    @Override
    public List<OutBoxEvent> findPendingForPublish(int limit) {

        List<OutboxEventRow> query = jdbcTemplate.query(
                """
                        SELECT * FROM outbox_events
                        WHERE status = 'PENDING'
                          AND (next_retry_at IS NULL OR next_retry_at <= now())
                        ORDER BY created_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        ;
                       \s""", rowMapper, limit);

        return query.stream()
                .map(domainMapper::toDomain)
                .toList();
    }

    @Override
    public void markPublished(UUID eventId, Instant timestamp) {
        jdbcTemplate.update(
                """
                    UPDATE outbox_events
                    SET status = 'PUBLISHED', published_at = ?, last_error = NULL, next_retry_at = NULL
                    WHERE event_id = ?;
                    """, Timestamp.from(timestamp), eventId);
    }

    @Override
    public void scheduleRetry(UUID eventId, String lastError, Instant nextRetryAt) {
        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET status = 'PENDING',
                    next_retry_at = ?,
                    retry_count = retry_count + 1,
                    last_error = ?,
                    published_at = NULL
                WHERE event_id = ?;
                """, Timestamp.from(nextRetryAt), lastError, eventId);

    }

    @Override
    public void markFailed(UUID eventId, String lastError) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'FAILED',
                    retry_count = retry_count + 1,
                    last_error = ?,
                    next_retry_at = NULL,
                    published_at = NULL
                WHERE event_id = ?;
        """, lastError, eventId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
