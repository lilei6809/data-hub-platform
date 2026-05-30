package io.datahub.platform.iamprovisioning.interfaces.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.port.in.HandleTenantIamOnboardingEventUseCase;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import io.datahub.platform.iamprovisioning.interfaces.messaging.dto.TenantInfrastructureProvisionedEventDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class TenantIamKafkaConsumer {

    private final HandleTenantIamOnboardingEventUseCase useCase;
    private final ObjectMapper objectMapper;
    private final TenantInfrastructureProvisionedEventTranslator translator;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${cdp.kafka.topic.tenant-infrastructure-provisioned-dlt}")
    private String dltTopic;

    public TenantIamKafkaConsumer(HandleTenantIamOnboardingEventUseCase useCase, ObjectMapper objectMapper, TenantInfrastructureProvisionedEventTranslator translator, KafkaTemplate<String, String> kafkaTemplate) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
        this.translator = translator;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "${cdp.kafka.topic.tenant-infrastructure-provisioned}",
            groupId = "iam-provisioning-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack){

        String rawPayload = record.value();

        // === 阶段 1：反序列化（业务尚未启动，失败进 DLT）===
        TenantInfrastructureProvisionedEventDto dto;
        try{
            dto = objectMapper.readValue(rawPayload, TenantInfrastructureProvisionedEventDto.class);
        } catch (JsonProcessingException e) {
            // 无效 JSON，不可重试，直接进 DLT
            log.error("Failed to deserialize message, routing to DLT. payload={}", rawPayload, e);
            routeToDlt(record, "deserialization-failure");

            // 需要 ack
            ack.acknowledge();
            return;
        }


        // === 阶段 2：ACL 翻译（业务尚未启动，失败进 DLT）===
        TenantInfrastructureProvisionedEvent event = null;

        try{
            event = translator.translate(dto);
        } catch (DomainValidationException e){
            log.error("Invalid event fields, tenantId={}, routing to DLT", dto.tenantId(), e);
            routeToDlt(record, "validation-failure");
            ack.acknowledge();
            return;
        }


        // === 阶段 3：调用用例（业务已启动，结果由状态机管理）===
        try {
            useCase.handle(event);

            // 成功：状态 IAM_COMPLETED，成功事件已发布
            ack.acknowledge();
        } catch (IamProvisioningException e){
            // retryable=true：状态 AWAITING_RETRY，RetryScheduler 接管
            // retryable=false：状态 IAM_FAILED，终态
            // 两种情况下，业务状态均已持久化，Consumer 的职责已完成
            log.warn("IAM provisioning handled, retryable={}, tenantId={}",
                    e.retryable(), event.tenantId(), e);
            ack.acknowledge();
        } catch (Exception e){
            // 未预期错误：状态可能不完整，进 DLT 等待人工处理
            log.error("Unexpected error during IAM provisioning, tenantId={}, routing to DLT",
                    event.tenantId(), e);
            routeToDlt(record, "unexpected-error");
            ack.acknowledge();
        }
    }

    private void routeToDlt(ConsumerRecord<String, String> sourceRecord, String reason) {
        ProducerRecord<String, String> dltRecord = new ProducerRecord<>(dltTopic, sourceRecord.key(), sourceRecord.value());
        dltRecord.headers().add("failure-reason", reason.getBytes(StandardCharsets.UTF_8));
        dltRecord.headers().add("source-topic", sourceRecord.topic().getBytes(StandardCharsets.UTF_8));
        dltRecord.headers().add("source-partition", Integer.toString(sourceRecord.partition()).getBytes(StandardCharsets.UTF_8));
        dltRecord.headers().add("source-offset", Long.toString(sourceRecord.offset()).getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(dltRecord);
    }
}
