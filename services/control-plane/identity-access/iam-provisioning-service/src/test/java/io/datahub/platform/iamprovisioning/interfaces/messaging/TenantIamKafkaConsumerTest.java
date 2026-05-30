package io.datahub.platform.iamprovisioning.interfaces.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.IamProvisioningStep;
import io.datahub.platform.iamprovisioning.application.port.in.HandleTenantIamOnboardingEventUseCase;
import io.datahub.platform.iamprovisioning.config.kafka.properties.KafkaTopicProperties;
import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.interfaces.messaging.dto.TenantInfrastructureProvisionedEventDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantIamKafkaConsumerTest {

    @Mock
    HandleTenantIamOnboardingEventUseCase useCase;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;


    @Mock
    Acknowledgment ack;

    ObjectMapper objectMapper = new ObjectMapper();

    KafkaTopicProperties kafkaTopicProperties;

    TenantIamKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        kafkaTopicProperties = new KafkaTopicProperties();
        kafkaTopicProperties.setTenantInfrastructureProvisioned("cdp.infrastructure.tenant.provisioned");
        kafkaTopicProperties.setTenantInfrastructureProvisionedDlt("cdp.infrastructure.tenant.provisioned.dlt");
        kafkaTopicProperties.setTenantIamProvisioned("cdp.iam.tenant.provisioned");
        kafkaTopicProperties.setTenantIamProvisionFailed("cdp.iam.tenant.provision-failed");

        consumer = new TenantIamKafkaConsumer(useCase,
                objectMapper,
                new TenantInfrastructureProvisionedEventTranslator(),
                kafkaTemplate,
                kafkaTopicProperties, null);
    }


    @Test
    @DisplayName("TenantIamKafkaConsumerTest: 合法消息成功处理：应调用用例并 Ack，不路由到 DLT")
    void consume_validMessage_success_shouldHandleAndAck(){
        // arrange
        String validJson = buildValidJson("tenant-abc",  "abc@abc.com");
        ConsumerRecord<String, String> record = buildConsumeRecord(validJson);
        TenantInfrastructureProvisionedEvent event = buildDomainEvent(validJson);

        // act
        consumer.consume(record, ack);

        // assert
        verify(useCase, times(1)).handle(event);
        verify(ack, times(1)).acknowledge();
        verifyNoInteractions(kafkaTemplate); // DLT 未被调用
    }

    @Test
    @DisplayName("TenantIamKafkaConsumerTest: 可重试业务失败：应 Ack 但不进 DLT，RetryScheduler 接管")
    void consume_validMessage_retryableProvisioningFailure_shouldAckWithoutDlt(){
        // arrange
        String validJson = buildValidJson("tenant-abc",  "abc@abc.com");
        ConsumerRecord<String, String> record = buildConsumeRecord(validJson);
        TenantInfrastructureProvisionedEvent event = buildDomainEvent(validJson);

        doThrow(new IamProvisioningException(
                IamProvisioningStep.ENSURE_ORGANIZATION,
                IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE,
                "keycloak 503",
                true,
                null
        )).when(useCase).handle(any());

        consumer.consume(record, ack);

        // assert
        verify(useCase, times(1)).handle(event);
        verify(ack, times(1)).acknowledge();
        verifyNoInteractions(kafkaTemplate); // DLT 未被调用
    }


    @Test
    @DisplayName("TenantIamKafkaConsumerTest: 不可重试业务失败：应 Ack , IAM_FAILED, 不进 dlt")
    void consume_validMessage_non_retryableProvisioningFailure_shouldAckWithoutDlt(){
        // arrange
        String validJson = buildValidJson("tenant-abc",  "abc@abc.com");
        ConsumerRecord<String, String> record = buildConsumeRecord(validJson);
        TenantInfrastructureProvisionedEvent event = buildDomainEvent(validJson);

        doThrow(new IamProvisioningException(
                IamProvisioningStep.ENSURE_ORGANIZATION,
                IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED,
                "keycloak 403",
                false,
                null
        )).when(useCase).handle(any());

        consumer.consume(record, ack);

        // assert
        verify(useCase, times(1)).handle(event);
        verify(ack, times(1)).acknowledge();
        verifyNoInteractions(kafkaTemplate); // DLT 未被调用
    }


    @Test
    @DisplayName("TenantIamKafkaConsumerTest: 非法 JSON：应进 DLT 并 Ack，用例从不被调用")
    void consume_invalidJson_shouldRouteToDltAndAck(){
        String invalid = "invalid json";
        ConsumerRecord<String, String> record = buildConsumeRecord(invalid);

        consumer.consume(record, ack);

        verify(useCase, never()).handle(any());
        verify(ack, times(1)).acknowledge();

        /**
         * ArgumentCaptor 是 Mockito 里用来“抓住 mock 方法调用时传进去的真实参数”的工具。
         *
         *   你这里的场景是：
         *
         *   verify(kafkaTemplate).send(dltRecordCaptor.capture());
         *   ProducerRecord<String, String> dltRecord = dltRecordCaptor.getValue();
         *
         *   意思是：
         *
         *   1. 验证 kafkaTemplate.send(...) 确实被调用了。
         *   2. 把当时传进去的那个 ProducerRecord 抓出来。
         *   3. 后面再检查这个对象里面的内容：
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<ProducerRecord<String, String>> dltRecordCaptor = ArgumentCaptor.forClass((Class) ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(dltRecordCaptor.capture());

        ProducerRecord<String, String> dltRecord = dltRecordCaptor.getValue();
        Header failureReason = dltRecord.headers().lastHeader("failure-reason");
        Header sourceTopic = dltRecord.headers().lastHeader("source-topic");
        Header sourcePartition = dltRecord.headers().lastHeader("source-partition");
        Header sourceOffset = dltRecord.headers().lastHeader("source-offset");

        assertThat(dltRecord.topic()).isEqualTo(kafkaTopicProperties.getTenantInfrastructureProvisionedDlt());
        assertThat(dltRecord.key()).isEqualTo("tenant-abc");
        assertThat(dltRecord.value()).isEqualTo(invalid);
        assertThat(failureReason).isNotNull();
        assertThat(new String(failureReason.value(), StandardCharsets.UTF_8)).isEqualTo("deserialization-failure");
        assertThat(sourceTopic).isNotNull();
        assertThat(new String(sourceTopic.value(), StandardCharsets.UTF_8)).isEqualTo(kafkaTopicProperties.getTenantInfrastructureProvisioned());
        assertThat(sourcePartition).isNotNull();
        assertThat(new String(sourcePartition.value(), StandardCharsets.UTF_8)).isEqualTo("0");
        assertThat(sourceOffset).isNotNull();
        assertThat(new String(sourceOffset.value(), StandardCharsets.UTF_8)).isEqualTo("0");
    }

    @Test
    @DisplayName("TenantIamKafkaConsumerTest: 合法 json, 但是领域对象字段非法(如: email非法), 应进 DLT 并 Ack")
    void consume_invalidDomainFields_shouldRouteToDltAndAck() {
        String s = buildValidJson("tenant-abc", "email-----");
        ConsumerRecord<String, String> record = buildConsumeRecord(s);

        consumer.consume(record, ack);

        verify(useCase, never()).handle(any());
        verify(ack, times(1)).acknowledge();

        ArgumentCaptor<ProducerRecord<String, String>> dltRecordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(dltRecordCaptor.capture());

        ProducerRecord<String, String> dltRecord = dltRecordCaptor.getValue();
        Header failureReason = dltRecord.headers().lastHeader("failure-reason");
        Header sourceTopic = dltRecord.headers().lastHeader("source-topic");
        Header sourcePartition = dltRecord.headers().lastHeader("source-partition");
        Header sourceOffset = dltRecord.headers().lastHeader("source-offset");

        assertThat(dltRecord.topic()).isEqualTo(kafkaTopicProperties.getTenantInfrastructureProvisionedDlt());
        assertThat(dltRecord.key()).isEqualTo("tenant-abc");
        assertThat(dltRecord.value()).isEqualTo(s);

        assertThat(failureReason).isNotNull();
        assertThat(new String(failureReason.value(), StandardCharsets.UTF_8)).isEqualTo("validation-failure");
        assertThat(sourceTopic).isNotNull();
        assertThat(new String(sourceTopic.value(), StandardCharsets.UTF_8)).isEqualTo(kafkaTopicProperties.getTenantInfrastructureProvisioned());
        assertThat(sourcePartition).isNotNull();
        assertThat(new String(sourcePartition.value(), StandardCharsets.UTF_8)).isEqualTo("0");
        assertThat(sourceOffset).isNotNull();
        assertThat(new String(sourceOffset.value(), StandardCharsets.UTF_8)).isEqualTo("0");

    }


    private ConsumerRecord<String, String> buildConsumeRecord(String json) {
        return new ConsumerRecord<>(
                kafkaTopicProperties.getTenantInfrastructureProvisioned(),
                0,
                0L,
                "tenant-abc",
                json
        );
    }

    private String buildValidJson(String tenantId, String email) {
        return """
        {
          "tenantId": "%s",
          "tenantName": "abc",
          "tier": "BASIC",
          "email": "%s",
          "correlationId": "%s",
          "occurredAt": "%s"
        }
        """.formatted(
                tenantId,
                email,
                UUID.randomUUID().toString(),
                Instant.ofEpochMilli(0L)
        );
    }

    private TenantInfrastructureProvisionedEvent buildDomainEvent(String json)  {
        TenantInfrastructureProvisionedEventDto dto = null;
        try {
            dto = objectMapper.readValue(json, TenantInfrastructureProvisionedEventDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return TenantInfrastructureProvisionedEvent.of(
                TenantId.of(dto.tenantId()),
                TenantName.of(dto.tenantName()),
                Tier.of(dto.tier()),
                Email.of(dto.email()),
                CorrelationId.of(dto.correlationId()),
                Instant.parse(dto.occurredAt())
        );
    }
}
