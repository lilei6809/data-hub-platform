package io.datahub.platform.iamprovisioning.application.port.out.vault;


public interface SecretStorePort {

    Secret getSecret(SecretKey secretKey);
}
