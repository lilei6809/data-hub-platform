package io.datahub.platform.iamprovisioning.config.keycloak;

import io.datahub.platform.iamprovisioning.config.keycloak.properties.KeycloakAdminProperty;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class KeycloakAdminClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "cdp.keycloak.admin.enabled",
            havingValue = "true",
            matchIfMissing = false)
    public Keycloak keycloakAdminClient(KeycloakAdminProperty props) {
        // Phase 4 接入 Vault 后，改为从 SecretStorePort 获取凭证。
        // 当前阶段凭证通过环境变量（KEYCLOAK_ADMIN_CLIENT_CREDENTIAL）注入，
        // 绑定到 KeycloakAdminProperty.clientCredential，无需走 SecretStore。
        String credential = props.getClientCredential();

        log.info("Keycloak admin client credential: {}", credential);
        if (credential == null || credential.isBlank()) {
            throw new IllegalStateException(
                    "cdp.keycloak.admin.client-credential is required when keycloak admin is enabled");
        }

        log.info("Creating KeycloakAdminClient serverUrl={} realm={} clientId={}",
                props.getServerUrl(), props.getRealm(), props.getAdminClientId());

        return KeycloakBuilder.builder()
                .serverUrl(props.getServerUrl())
                .clientId(props.getAdminClientId())
                .realm(props.getRealm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientSecret(credential)
                .build();
    }
}
