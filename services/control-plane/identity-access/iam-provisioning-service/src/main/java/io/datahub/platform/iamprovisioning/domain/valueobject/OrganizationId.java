package io.datahub.platform.iamprovisioning.domain.valueobject;

// Keycloak 返回的 orgId
public record OrganizationId(String value) {

    public static OrganizationId of(String value) {
        return new OrganizationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
