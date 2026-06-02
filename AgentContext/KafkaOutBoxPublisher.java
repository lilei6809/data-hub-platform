package io.datahub.platform.iamprovisioning.infrastructure.messaging;

import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * notion:  https://www.notion.so/KafkaOutBoxPublisher-372cb22c4335805d846aeec755022beb?source=copy_link
 */
@Component
@Slf4j
public class KafkaOutBoxPublisher {

    private static final int MAX_FAILURE_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 5;
    private static final int RETRY_DELAY_SECONDS = 10;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOutBoxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }


    @Scheduled(fixedDelayString = "${cdp.outbox.poll-interval-ms:2000}")
    public void pollAndPublish() {
        List<OutBoxEvent> events = repository.findPendingForPublish(BATCH_SIZE);

        for (OutBoxEvent event : events) {
            try {
                ProducerRecord<String, String> record = toProducerRecord(event);

                // .get() = 同步等待 Kafka ACK
                // 超时由 producer 配置的 delivery.timeout.ms 控制
                kafkaTemplate.send(record).get();

                repository.markPublished(event.eventId(), Instant.now());
            } catch (InterruptedException e) {

                // 多线程环境必须有的一个 catch 与处理
                Thread.currentThread().interrupt();
                recordFailedAttempt(event, e);
                return;
            } catch (Exception e) {
                // 失败：本层处理, 记录错误，判断是否达到最大重试次数
                // 异常吞掉
                log.atWarn()
                        .addKeyValue("event_id", event.eventId())
                        .addKeyValue("event_type", event.eventType())
                        .addKeyValue("aggregate_id", event.aggregateId())
                        .addKeyValue("attempt", event.retryCount() + 1)
                        .log("Outbox publish failed, will retry", e);
                recordFailedAttempt(event, e);
            }
        }
    }

    private ProducerRecord<String, String> toProducerRecord(OutBoxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.aggregateId(), event.payload());

        record.headers()
                .add("event_id", event.eventId().toString().getBytes(StandardCharsets.UTF_8))
                .add("event_type", event.eventType().getBytes(StandardCharsets.UTF_8))
                .add("correlation_id", event.correlationId().getBytes(StandardCharsets.UTF_8));

        if (event.causationId() != null && !event.causationId().isBlank()) {
            record.headers().add("causation_id", event.causationId().getBytes(StandardCharsets.UTF_8));
        }

        return record;
    }

    private void recordFailedAttempt(OutBoxEvent event, Exception e) {
        String lastError = failureMessage(e);
        if (event.retryCount() + 1 >= MAX_FAILURE_ATTEMPTS) {
            repository.markFailed(event.eventId(), lastError);
        } else {
            repository.scheduleRetry(event.eventId(), lastError, Instant.now().plusSeconds(RETRY_DELAY_SECONDS));
        }
    }

    private String failureMessage(Exception e) {
        // ExecutionException 和普通异常有本质区别。大多数异常直接描述了"出了什么问题"，
        // 但 ExecutionException 描述的是"在另一个线程里，出了某个问题"。
        // 它本身不携带任何有意义的业务信息，真正的错误被包裹在它的 cause 里。
        // 这种设计来自 Future.get() 的契约
        Throwable failure = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }
}
