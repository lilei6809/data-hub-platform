package io.datahub.platform.iamprovisioning.domain.valueobject;

/**
 * First-login actions required from a newly provisioned tenant administrator.
 *
 * <p>Adapters map these actions to provider-specific values, such as Keycloak
 * required actions. Keeping this enum in the domain model prevents Keycloak
 * constants from leaking into provisioning rules.
 */
public enum RequiredLoginAction {

    /**
     * The administrator must set a permanent password during the first login.
     */
    UPDATE_PASSWORD
}
