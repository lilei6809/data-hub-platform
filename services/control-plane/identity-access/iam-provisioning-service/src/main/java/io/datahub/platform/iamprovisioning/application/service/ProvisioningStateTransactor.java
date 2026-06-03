package io.datahub.platform.iamprovisioning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutBoxEvent;
import io.datahub.platform.iamprovisioning.application.port.out.repository.OutboxRepository;
import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.config.kafka.properties.KafkaTopicProperties;
import io.datahub.platform.iamprovisioning.domain.event.DomainEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisioningFailedEvent;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.dto.TenantIamProvisionedEventDto;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.dto.TenantIamProvisionedEventDtoMapper;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.dto.TenantIamProvisioningFailedEventDto;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.dto.TenantIamProvisioningFailedEventDtoMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// application/service/ProvisioningStateTransactor.java
// 职责：提供"状态持久化 + outbox 写入"的原子事务操作
// 命名：Transactor 表示"提供事务边界的辅助组件"
@Component
public class ProvisioningStateTransactor {

    private final TenantIamProvisioningStateRepository iamProvisioningStateRepository;
    private final OutboxRepository  outboxRepository;
    private final ObjectMapper mapper;
    private final KafkaTopicProperties kafkaTopicProperties;

    public ProvisioningStateTransactor(TenantIamProvisioningStateRepository iamProvisioningStateRepository, OutboxRepository outboxRepository, ObjectMapper mapper, KafkaTopicProperties kafkaTopicProperties) {
        this.iamProvisioningStateRepository = iamProvisioningStateRepository;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
        this.kafkaTopicProperties = kafkaTopicProperties;
    }

    // 核心方法：在一个事务中完成"保存状态 + 写入 outbox"
    @Transactional
    public void saveIamProvisionStateAndAppendEvents(TenantIamProvisioningState state) {




        // Step 1: 取出聚合根收集的事件，转换为 outbox 记录
        List<DomainEvent> domainEvents = state.drainEvents();


        List<OutBoxEvent> outBoxEvents = domainEvents.stream().map(
                domainEvent -> toOutBoxEvent(state, domainEvent)
        ).toList();


        // Step 2: 持久化聚合状态, 先确保上面没有问题, 再持久化
        // a. 持久化 state
        iamProvisioningStateRepository.save(state);
        // b. 持久化待发布的 events. 由 schedule 任务进行扫描发布
         if (outBoxEvents.size() > 0) {
             outboxRepository.appendAll(outBoxEvents);
         }
    }

    private OutBoxEvent toOutBoxEvent(TenantIamProvisioningState state, DomainEvent domainEvent) {
        return OutBoxEvent.pending(
                "TenantIamProvisioningState",
                state.getTenantId().value(),
                domainEvent.getClass().getSimpleName(),
                1,
                resolveTopic(domainEvent),
                serializeDomainEvent(domainEvent),
                serializeHeaders(state, domainEvent),
                state.getWorkflowCorrelationId().value(),
                "TenantInfrustructureProvisioned 的 ID",
                domainEvent.occurredAt(),
                Instant.now()
        );
    }

    public String resolveTopic(DomainEvent domainEvent) {
        if (domainEvent instanceof TenantIamProvisionedEvent)
            return kafkaTopicProperties.getTenantIamProvisioned();

        if (domainEvent instanceof TenantIamProvisioningFailedEvent)
            return kafkaTopicProperties.getTenantIamProvisionFailed();
        throw new IllegalStateException("Unknown event type: " + domainEvent.getClass().getSimpleName());

    }


    private String serializeHeaders(TenantIamProvisioningState state, DomainEvent domainEvent) {
        // TODO: 需要构建 Header 值对象
        return "{}";
    }

    private String serializeDomainEvent(DomainEvent domainEvent) {
        try {

            if (domainEvent instanceof TenantIamProvisionedEvent event){
                TenantIamProvisionedEventDto dto = TenantIamProvisionedEventDtoMapper.toDto(event);

                return mapper.writeValueAsString(dto);
            }

            if (domainEvent instanceof TenantIamProvisioningFailedEvent event){
                TenantIamProvisioningFailedEventDto dto = TenantIamProvisioningFailedEventDtoMapper.toDto(event);
                return mapper.writeValueAsString(dto);
            }



        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize domain event: " + domainEvent.getClass(), e);
        }

        throw new IllegalStateException("Unknown domain event type: " + domainEvent.getClass().getSimpleName());
    }
}
