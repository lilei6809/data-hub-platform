package io.datahub.platform.iamprovisioning.application.port.out.vault;

import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

public class SecretKey {

    private final String identifier;

    private SecretKey(String identifier) {
        this.identifier = identifier;
    }

    public static SecretKey keycloakAdmin() {
        return new SecretKey("keycloak/admin-client-secret");
    }

    public static SecretKey idpClientSecret(TenantId tenantId, String idpAlias){
        return new SecretKey("tenants/" + tenantId.value() + "/idp/" + idpAlias);
    }

    //还有获取 vault 中的 kafka credential, 数据库 credential 的 SecretKey 的方法
}
