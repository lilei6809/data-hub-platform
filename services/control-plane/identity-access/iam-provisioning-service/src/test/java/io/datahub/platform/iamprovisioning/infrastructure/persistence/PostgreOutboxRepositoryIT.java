package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventDomainMapper;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper.OutboxEventRowMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgreOutboxRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iam_provisioning_outbox_test")
                    .withUsername("test")
                    .withPassword("test");

    private static JdbcTemplate jdbcTemplate;

    private final OutboxEventRowMapper rowMapper = new OutboxEventRowMapper();
    private final OutboxEventDomainMapper domainMapper = new OutboxEventDomainMapper();

    private PostgreOutboxRepository repository;

    @BeforeAll
    static void migrate() {
        DataSource dataSource = new DriverManagerDataSource(
                postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword()
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("TRUNCATE TABLE outbox_events");
        repository = new PostgreOutboxRepository(jdbcTemplate);
    }

    @Test
    void appendAll_persists_pending_event_with_database_id_and_causation_id() {
        OutBoxEvent event = pendingEvent("tenant-alpha");

        repository.appendAll(List.of(event));

        OutBoxEvent saved = loadByAggregateId("tenant-alpha");

        assertThat(saved).satisfies(persisted -> {
            assertThat(persisted.eventId()).isNotNull();
            assertThat(persisted.aggregateType()).isEqualTo("TenantIamProvisioningState");
            assertThat(persisted.aggregateId()).isEqualTo("tenant-alpha");
            assertThat(persisted.eventType()).isEqualTo("TenantIamProvisionedEvent");
            assertThat(persisted.eventVersion()).isEqualTo(1);
            assertThat(persisted.topic()).isEqualTo("cdp.iam.tenant.provisioned");
            assertThat(persisted.payload()).contains("\"tenantId\": \"tenant-alpha\"");
            assertThat(persisted.headers()).contains("\"contentType\": \"application/json\"");
            assertThat(persisted.status()).isEqualTo(OutBoxEvent.Status.PENDING);
            assertThat(persisted.retryCount()).isZero();
            assertThat(persisted.lastError()).isNull();
            assertThat(persisted.correlationId()).isEqualTo("correlation-tenant-alpha");
            assertThat(persisted.causationId()).isEqualTo("causation-tenant-alpha");
            assertThat(persisted.occurredAt()).isEqualTo(Instant.parse("2026-06-01T10:15:30Z"));
            assertThat(persisted.createdAt()).isNotNull();
            assertThat(persisted.publishedAt()).isNull();
        });
    }

    @Test
    void claimBatch_excludes_events_scheduled_for_future_retry() {
        OutBoxEvent ready = appendOne("tenant-ready");
        OutBoxEvent futureRetry = appendOne("tenant-future");

        repository.scheduleRetry(futureRetry.eventId(), "broker unavailable", Instant.now().plusSeconds(3600));

        List<OutBoxEvent> claimed = repository.claimBatch(10, "publisher-1");

        assertThat(claimed)
                .extracting(OutBoxEvent::eventId)
                .containsExactly(ready.eventId());
        assertThat(load(ready.eventId()).status()).isEqualTo(OutBoxEvent.Status.CLAIMING);
        assertThat(load(futureRetry.eventId()).status()).isEqualTo(OutBoxEvent.Status.PENDING);
    }

    @Test
    void markPublished_sets_published_status_and_timestamp() {
        OutBoxEvent event = appendOne("tenant-published");
        Instant publishedAt = Instant.parse("2026-06-01T11:00:00Z");

        repository.markPublished(event.eventId(), publishedAt);

        OutBoxEvent saved = load(event.eventId());
        assertThat(saved.status()).isEqualTo(OutBoxEvent.Status.PUBLISHED);
        assertThat(saved.publishedAt()).isEqualTo(publishedAt);
        assertThat(saved.nextRetryAt()).isNull();
        assertThat(saved.lastError()).isNull();
    }

    @Test
    void scheduleRetry_sets_retry_metadata_and_keeps_event_pending() {
        OutBoxEvent event = appendOne("tenant-retry");
        Instant nextRetryAt = Instant.parse("2026-06-01T11:05:00Z");

        repository.scheduleRetry(event.eventId(), "temporary kafka failure", nextRetryAt);

        OutBoxEvent saved = load(event.eventId());
        assertThat(saved.status()).isEqualTo(OutBoxEvent.Status.PENDING);
        assertThat(saved.retryCount()).isEqualTo(1);
        assertThat(saved.lastError()).isEqualTo("temporary kafka failure");
        assertThat(saved.nextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(saved.publishedAt()).isNull();
    }

    @Test
    void markFailed_sets_failed_status_and_records_error() {
        OutBoxEvent event = appendOne("tenant-failed");

        repository.markFailed(event.eventId(), "permanent serialization failure");

        OutBoxEvent saved = load(event.eventId());
        assertThat(saved.status()).isEqualTo(OutBoxEvent.Status.FAILED);
        assertThat(saved.retryCount()).isEqualTo(1);
        assertThat(saved.lastError()).isEqualTo("permanent serialization failure");
        assertThat(saved.nextRetryAt()).isNull();
        assertThat(saved.publishedAt()).isNull();
    }

    private OutBoxEvent appendOne(String tenantId) {
        repository.appendAll(List.of(pendingEvent(tenantId)));
        return loadByAggregateId(tenantId);
    }

    private OutBoxEvent load(UUID eventId) {
        OutboxEventRow row = jdbcTemplate.queryForObject(
                "SELECT * FROM outbox_events WHERE event_id = ?",
                rowMapper,
                eventId
        );
        return domainMapper.toDomain(row);
    }

    private OutBoxEvent loadByAggregateId(String aggregateId) {
        OutboxEventRow row = jdbcTemplate.queryForObject(
                "SELECT * FROM outbox_events WHERE aggregate_id = ?",
                rowMapper,
                aggregateId
        );
        return domainMapper.toDomain(row);
    }

    private OutBoxEvent pendingEvent(String tenantId) {
        return OutBoxEvent.pending(
                "TenantIamProvisioningState",
                tenantId,
                "TenantIamProvisionedEvent",
                1,
                "cdp.iam.tenant.provisioned",
                "{\"tenantId\":\"%s\"}".formatted(tenantId),
                "{\"contentType\":\"application/json\"}",
                "correlation-" + tenantId,
                "causation-" + tenantId,
                Instant.parse("2026-06-01T10:15:30Z"),
                Instant.parse("2026-06-01T10:15:31Z")
        );
    }
}
