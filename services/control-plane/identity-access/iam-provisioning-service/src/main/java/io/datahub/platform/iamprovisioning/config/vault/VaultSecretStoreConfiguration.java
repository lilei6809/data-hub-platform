package io.datahub.platform.iamprovisioning.config.vault;

import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretStorePort;
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
            havingValue = "fake",
            matchIfMissing = false
    )
    public SecretStorePort fakeVaultSecretStore() {
        return new InMemorySecretStore();
    }


    @Bean
    @ConditionalOnProperty(
            name = "cdp.vault.adapter.type",
            havingValue = "real",
            matchIfMissing = false
    )
    public SecretStorePort realVaultSecretStore() {
        return new VaultSecretStore();
    }
}
