package io.datahub.platform.iamprovisioning.infrastructure.secrets;

import io.datahub.platform.iamprovisioning.application.port.out.vault.Secret;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretKey;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretStorePort;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySecretStore implements SecretStorePort {

    private final ConcurrentHashMap<SecretKey, Secret> store = new ConcurrentHashMap<>();

    @Override
    public Secret getSecret(SecretKey secretKey) {
        return store.get(secretKey);
    }

    public void putSecret(SecretKey secretKey, Secret secret) {
        store.put(secretKey, secret);
    }
}
