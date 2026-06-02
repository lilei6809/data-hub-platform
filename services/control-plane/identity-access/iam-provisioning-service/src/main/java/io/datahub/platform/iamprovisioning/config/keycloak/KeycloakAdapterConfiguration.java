package io.datahub.platform.iamprovisioning.config.keycloak;

import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.config.keycloak.properties.KeycloakAdminProperty;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.FakeKeycloakAdminPort;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.RealKeycloakAdminPortAdapter;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdapterConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "cdp.keycloak.adapter.real.enabled",
            havingValue = "false"
    )
    public KeycloakAdminPort fakeKeycloakAdminPort() {
        return new FakeKeycloakAdminPort();
    }

    @Bean
    @ConditionalOnProperty(name = "cdp.keycloak.adapter.real.enabled", havingValue = "true")
    public KeycloakAdminPort realKeycloakAdminPort(Keycloak keycloak, KeycloakAdminProperty props) {
        return new RealKeycloakAdminPortAdapter(keycloak, props.getRealm());
    }
}
