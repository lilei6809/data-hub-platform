package io.datahub.platform.iamprovisioning.infrastructure.secrets;

import io.datahub.platform.iamprovisioning.application.port.out.vault.Secret;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretKey;
import io.datahub.platform.iamprovisioning.application.port.out.vault.SecretStorePort;

public class VaultSecretStore implements SecretStorePort {
    @Override
    public Secret getSecret(SecretKey secretKey) {
        return null;
    }
}
