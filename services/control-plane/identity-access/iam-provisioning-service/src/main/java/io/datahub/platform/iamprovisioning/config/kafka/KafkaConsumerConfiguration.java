package io.datahub.platform.iamprovisioning.config.kafka;

import com.fasterxml.jackson.core.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "cdp.kafka.consumer.enabled", havingValue = "true") // 在测试环境（cdp.kafka.consumer.enabled=false）时 Kafka Consumer 不会启动，复用现有的内存测试方式
@Slf4j
public class KafkaConsumerConfiguration {

    @Value("${cdp.kafka.consumer.concurrency}")
    private int concurrency;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();

        containerFactory.setConsumerFactory(consumerFactory);
        containerFactory.setConcurrency(concurrency);

        // 手动 Ack 模式是 Persist-before-Ack 的前提
        containerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 配置 consumer 线程数
        containerFactory.setConcurrency(concurrency);


        //================================================
        // 我们的 consumer 中已经兜底了所有的 Exception, 遇到 Exception 这个 errorHandler 是不会生效的
        // 作为防御性编程的配置, 防止 Exception 兜底处理的逻辑被修改

        // 错误处理：配置 Dead Letter Topic
        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1, 3)
        );


        // 不可重试的异常直接发 DLT，不重试
        defaultErrorHandler.addNotRetryableExceptions(JsonParseException.class);
        containerFactory.setCommonErrorHandler(defaultErrorHandler);

        return containerFactory;
    }
}
