package io.datahub.platform.iamprovisioning.infrastructure.messaging;

import io.datahub.platform.iamprovisioning.application.port.out.EventPublisher;
import io.datahub.platform.iamprovisioning.config.kafka.properties.KafkaTopicProperties;
import io.datahub.platform.iamprovisioning.domain.event.DomainEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisioningFailedEvent;
import io.datahub.platform.iamprovisioning.domain.exception.EventPublishException;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.dto.TenantIamProvisionedEventDto;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.dto.TenantIamProvisioningFailedEventDto;
import io.datahub.platform.iamprovisioning.interfaces.messaging.dto.TenantInfrastructureProvisionedEventDto;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;


public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTopicProperties kafkaTopicProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTopicProperties kafkaTopicProperties, KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(DomainEvent iamProvisionEvent) throws EventPublishException {

        try {
            if (iamProvisionEvent instanceof TenantIamProvisionedEvent e) {
                sendToKafka(kafkaTopicProperties.getTenantIamProvisioned(),
                        e.tenantId(),
                        toDto(e),
                        e.correlationId()
                        );
            } else if (iamProvisionEvent instanceof TenantIamProvisioningFailedEvent e) {
                sendToKafka(kafkaTopicProperties.getTenantIamProvisionFailed(),
                        e.tenantId(),
                        toDto(e),
                        e.correlationId());
            } else {
                // 否则序列化错误, 必须显式抛错，不能静默忽略
                throw new EventPublishException("Unsupported event type: " + iamProvisionEvent.getClass().getName());
            }
        } catch (Exception ex) {
            //TODO: Log
            throw new EventPublishException("Failed to publish event: " + ex.getMessage(), ex);
        }
    }

    private void sendToKafka(String topic, TenantId tenantId, Object dto, CorrelationId correlationId) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, tenantId.value(), dto);

        record.headers().add("correlationId", correlationId.value().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
    }

    private TenantIamProvisionedEventDto toDto(TenantIamProvisionedEvent e) {
        return new TenantIamProvisionedEventDto(
                e.tenantId().value(),
                e.tier().value(),
                e.adminEmail().value(),
                e.correlationId().value(),
                e.occurredAt().toString()
        );
    }
    
    private TenantIamProvisioningFailedEventDto toDto(TenantIamProvisioningFailedEvent e) {
        return new TenantIamProvisioningFailedEventDto(
                e.tenantId().value(),
                e.tier().value(),
                e.failureCode().name(),
                String.valueOf(e.retryable()),
                e.correlationId().value(),
                e.occurredAt().toString()
        );
    }
}
