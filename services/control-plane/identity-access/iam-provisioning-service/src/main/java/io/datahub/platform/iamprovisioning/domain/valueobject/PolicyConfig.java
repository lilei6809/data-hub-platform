package io.datahub.platform.iamprovisioning.domain.valueobject;

/**
 * Extension placeholder for future tenant IAM policies.
 *
 * <p>The first wave does not implement MFA, password-policy, or login-policy
 * behavior here. Keeping this as a domain value object gives
 * TenantIamDesiredState a stable extension point without depending on a policy
 * engine, Keycloak SDK, or infrastructure configuration type.
 */
public record PolicyConfig() {
}

