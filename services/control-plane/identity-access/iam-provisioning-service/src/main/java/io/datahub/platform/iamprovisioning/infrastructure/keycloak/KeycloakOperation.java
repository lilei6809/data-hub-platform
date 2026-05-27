package io.datahub.platform.iamprovisioning.infrastructure.keycloak;

public enum KeycloakOperation {
    ENSURE_ORGANIZATION,
    ENSURE_USER,
    ENSURE_ORGANIZATION_MEMBERSHIP,
    ENSURE_REALM_ROLE,
    ENSURE_USER_REALM_ROLE
}
