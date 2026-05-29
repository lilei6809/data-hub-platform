package io.datahub.platform.iamprovisioning.config.vault;

import io.datahub.platform.iamprovisioning.application.port.out.vault.Secret;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretKey;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretStorePort;
import io.datahub.platform.iamprovisioning.config.keycloak.properties.KeycloakAdminProperty;
import io.datahub.platform.iamprovisioning.infrastructure.secrets.InMemorySecretStore;
import io.datahub.platform.iamprovisioning.infrastructure.secrets.VaultSecretStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VaultSecretStoreConfiguration {


    @Bean
    @ConditionalOnProperty(
            name = "cdp.vault.adapter.type",
            havingValue = "fake"
    )
    public SecretStorePort fakeVaultSecretStore(KeycloakAdminProperty keycloakAdminProperty) {
        InMemorySecretStore secretStore = new InMemorySecretStore();
        String clientCredential = keycloakAdminProperty.getClientCredential();
        if (clientCredential != null && !clientCredential.isBlank()) {
            secretStore.putSecret(SecretKey.keycloakAdmin(), new Secret(clientCredential));
        }
        return secretStore;
    }


    @Bean
    @ConditionalOnProperty(
            name = "cdp.vault.adapter.type",
            havingValue = "real"
    )
    public SecretStorePort realVaultSecretStore() {
        return new VaultSecretStore();
    }
}
