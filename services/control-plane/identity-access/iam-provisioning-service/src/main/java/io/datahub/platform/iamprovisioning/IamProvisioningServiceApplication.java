package io.datahub.platform.iamprovisioning;

import io.datahub.platform.iamprovisioning.config.keycloak.properties.KeycloakAdminProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KeycloakAdminProperty.class)
public class IamProvisioningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamProvisioningServiceApplication.class, args);
    }
}
