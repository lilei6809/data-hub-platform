package io.datahub.platform.iamprovisioning.config.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@ConditionalOnProperty(name = "cdp.kafka.consumer.enabled", havingValue = "true") // 在测试环境（cdp.kafka.consumer.enabled=false）时 Kafka Consumer 不会启动，复用现有的内存测试方式
@Slf4j
public class KafkaConsumerConfiguration {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();

        containerFactory.setConsumerFactory(consumerFactory);

        // 手动 Ack 模式是 Persist-before-Ack 的前提
        containerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return containerFactory;
    }
}
