package io.datahub.platform.iamprovisioning.infrastructure.messaging;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutBoxPublisherTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaOutBoxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaOutBoxPublisher(repository, kafkaTemplate);
    }

    @Test
    @DisplayName("Should publish pending event to Kafka and mark it published after broker ack")
    void pollAndPublish_success_shouldSendKafkaRecordAndMarkPublished() {
        OutBoxEvent event = pendingEvent(0);
        when(repository.claimBatch(eq(5), anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyProducerRecord())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.pollAndPublish();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass((Class) ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();

        assertThat(record.topic()).isEqualTo("topic-from-outbox");
        assertThat(record.key()).isEqualTo("tenant-alpha");
        assertThat(record.value()).isEqualTo("{\"tenantId\":\"tenant-alpha\"}");
        assertHeader(record, "event_id", event.eventId().toString());
        assertHeader(record, "event_type", "TenantIamProvisionedEvent");
        assertHeader(record, "correlation_id", "correlation-123");
        assertHeader(record, "causation_id", "causation-456");

        verify(repository).markPublished(eq(event.eventId()), any(Instant.class));
        verify(repository, never()).scheduleRetry(any(), any(), any());
        verify(repository, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("Should schedule retry when Kafka publish fails before max failure attempts")
    void pollAndPublish_publishFailureBeforeMaxAttempts_shouldScheduleRetry() {
        OutBoxEvent event = pendingEvent(2);
        CompletableFuture failedSend = new CompletableFuture();
        failedSend.completeExceptionally(new RuntimeException("broker unavailable"));
        when(repository.claimBatch(eq(5), anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyProducerRecord())).thenReturn(failedSend);
        Instant before = Instant.now();

        publisher.pollAndPublish();

        ArgumentCaptor<Instant> retryAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(repository).scheduleRetry(eq(event.eventId()), eq("broker unavailable"), retryAtCaptor.capture());

        assertThat(retryAtCaptor.getValue())
                .isAfterOrEqualTo(before.plusSeconds(10))
                .isBeforeOrEqualTo(Instant.now().plusSeconds(10));
        verify(repository, never()).markPublished(any(), any());
        verify(repository, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("Should mark event failed when Kafka publish reaches max failure attempts")
    void pollAndPublish_publishFailureAtMaxAttempts_shouldMarkFailed() {
        OutBoxEvent event = pendingEvent(4);
        CompletableFuture failedSend = new CompletableFuture();
        failedSend.completeExceptionally(new RuntimeException("broker unavailable"));
        when(repository.claimBatch(eq(5), anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyProducerRecord())).thenReturn(failedSend);

        publisher.pollAndPublish();

        verify(repository).markFailed(event.eventId(), "broker unavailable");
        verify(repository, never()).markPublished(any(), any());
        verify(repository, never()).scheduleRetry(any(), any(), any());
    }

    @Test
    @DisplayName("Should use exception class name when Kafka failure has no message")
    void pollAndPublish_publishFailureWithoutMessage_shouldPersistNonBlankLastError() {
        OutBoxEvent event = pendingEvent(1);
        CompletableFuture failedSend = new CompletableFuture();
        failedSend.completeExceptionally(new RuntimeException());
        when(repository.claimBatch(eq(5), anyString())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyProducerRecord())).thenReturn(failedSend);

        publisher.pollAndPublish();

        verify(repository).scheduleRetry(eq(event.eventId()), eq("RuntimeException"), any(Instant.class));
    }

    @SuppressWarnings("unchecked")
    private static ProducerRecord<String, String> anyProducerRecord() {
        return any(ProducerRecord.class);
    }

    private static void assertHeader(ProducerRecord<String, String> record, String name, String expectedValue) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as(name).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo(expectedValue);
    }

    private static OutBoxEvent pendingEvent(int retryCount) {
        return OutBoxEvent.rehydrate(
                UUID.randomUUID(),
                "TenantIamProvisioningState",
                "tenant-alpha",
                "TenantIamProvisionedEvent",
                1,
                "topic-from-outbox",
                "{\"tenantId\":\"tenant-alpha\"}",
                "{}",
                OutBoxEvent.Status.PENDING,
                retryCount,
                null,
                null,
                "correlation-123",
                "causation-456",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                null,
                null,
                null
        );
    }
}
