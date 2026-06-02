package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventRowMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.OutboxEventRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// notion: https://www.notion.so/Outbox-Publisher-PostgreSQL-Claim-then-Publish-372cb22c433580b2a8f8d26736755c3f?source=copy_link
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
    @Transactional
    public List<OutBoxEvent> claimBatch(int limit, String claimedBy) {

        // Step 1: SELECT FOR UPDATE SKIP LOCKED
        // 在事务内获取行锁，防止其他实例在同一瞬间拿到同样的行
        List<OutboxEventRow> rows = jdbcTemplate.query(
                """
                        SELECT * FROM outbox_events
                                WHERE status = 'PENDING'
                                  AND (next_retry_at IS NULL OR next_retry_at <= now())
                                ORDER BY created_at
                                LIMIT ?
                                FOR UPDATE SKIP LOCKED
                        """, rowMapper, limit);

        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: 立刻更新状态为 CLAIMING
        // 事务提交后，锁释放，但 status=CLAIMING 使其对其他 poller 持久不可见
        List<UUID> eventIds = rows.stream()
                .map(row -> row.eventId())
                .toList();

        for (UUID eventId : eventIds) {
            jdbcTemplate.update(
                    """
                        UPDATE outbox_events
                        SET status = 'CLAIMING', claimed_by = ?, claimed_at = now()
                        WHERE event_id = ?
                        """, claimedBy, eventId);
        }

        // 事务提交（方法返回时 Spring 自动 COMMIT）→ 锁释放
        // 但 status 已经从 PENDING 变成 CLAIMING
        // 其他实例的 WHERE status = 'PENDING' 查不到这些行了


        return rows.stream().map(domainMapper::toDomain).toList();
    }

    @Override
    public int reclaimStale(Duration staleThreshold) {
        // 不需要 @Transactional，单条 UPDATE 的 auto-commit 足够
        // 不需要 FOR UPDATE SKIP LOCKED，因为与 claimBatch 的目标集合不重叠
        int update = jdbcTemplate.update(
                """
                        UPDATE  outbox_events
                        SET status = 'PENDING', claimed_at = NULL, claimed_by = NULL
                        WHERE status = 'CLAIMING' AND 
                              claimed_at < now() - ? * INTERVAL '1 second'
                        """, staleThreshold.toSeconds());


        return update;
    }

    @Override
    public void markPublished(UUID eventId, Instant timestamp) {
        jdbcTemplate.update(
                """
                    UPDATE outbox_events
                    SET status = 'PUBLISHED',
                        published_at = ?,
                        last_error = NULL,
                        next_retry_at = NULL,
                        claimed_by = NULL,
                        claimed_at = NULL
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
                    published_at = NULL,
                    claimed_by = NULL,
                    claimed_at = NULL
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
                    published_at = NULL,
                    claimed_by = NULL,
                    claimed_at = NULL
                WHERE event_id = ?;
        """, lastError, eventId);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
