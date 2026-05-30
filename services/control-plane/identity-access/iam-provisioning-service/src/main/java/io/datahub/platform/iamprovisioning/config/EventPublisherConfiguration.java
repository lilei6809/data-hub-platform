package io.datahub.platform.iamprovisioning.config;

import io.datahub.platform.iamprovisioning.application.port.out.EventPublisher;
import io.datahub.platform.iamprovisioning.config.kafka.properties.KafkaTopicProperties;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.InMemoryEventPublisher;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.KafkaEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class EventPublisherConfiguration {

    private final KafkaTopicProperties kafkaTopicProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisherConfiguration(KafkaTopicProperties kafkaTopicProperties, KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Bean
    @ConditionalOnProperty(
            name = "cdp.kafka.producer.enabled",
            havingValue = "false"
    )
    public EventPublisher inMemoryEventPublisher(){

        return new InMemoryEventPublisher();
    }


    @Bean
    @ConditionalOnProperty(
            name = "cdp.kafka.producer.enabled",
            havingValue = "true"
    )
    public EventPublisher kafkaEventPublisher(){
        return new KafkaEventPublisher(kafkaTopicProperties, kafkaTemplate);
    }
}
