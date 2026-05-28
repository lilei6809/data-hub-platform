package io.datahub.platform.iamprovisioning.config.keycloak;

import io.datahub.platform.iamprovisioning.application.port.out.vault.Secret;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretKey;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretStorePort;
import io.datahub.platform.iamprovisioning.config.keycloak.properties.KeycloakAdminProperty;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "cdp.keycloak.admin.enabled",
            havingValue = "true",
            matchIfMissing = false) // cdp.keycloak.admin.enabled 为 true 才生效
    public Keycloak keycloakAdminClient(KeycloakAdminProperty props,
                                        SecretStorePort secretStore){

        Secret secret = secretStore.getSecret(SecretKey.keycloakAdmin());

        return KeycloakBuilder.builder()
                .serverUrl(props.getServerUrl())
                .clientId(props.getAdminClientId())
                .realm("master")
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientSecret(secret.reveal()).build();
    }


}
