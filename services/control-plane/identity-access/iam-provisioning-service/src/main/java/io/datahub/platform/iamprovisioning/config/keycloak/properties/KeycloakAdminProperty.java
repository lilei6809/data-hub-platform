package io.datahub.platform.iamprovisioning.config.keycloak.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = "cdp.keycloak.admin")
@Getter
@Setter
public class KeycloakAdminProperty {

    private String serverUrl;
    private String realm = "cdp"; // 默认值
    private String adminClientId;
    private String clientCredential;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
}
