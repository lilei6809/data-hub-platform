package io.datahub.platform.iamprovisioning.domain.valueobject;

public record TenantName(String tenantName) {

    public static   TenantName of(String tenantName) {
        return new TenantName(tenantName);
    }
}
