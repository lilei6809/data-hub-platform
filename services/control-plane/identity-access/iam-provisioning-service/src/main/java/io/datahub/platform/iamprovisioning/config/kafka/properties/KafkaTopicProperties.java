package io.datahub.platform.iamprovisioning.config.kafka.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cdp.kafka.topics")
@Getter
@Setter
public class KafkaTopicProperties {

//    Inbound: 消费的 topic,  消费失败的 dlt
    private String tenantInfrastructureProvisioned;
    private String tenantInfrastructureProvisionedDlt;

    // outbound
    private String tenantIamProvisioned;
    private String tenantIamProvisionFailed;
}
