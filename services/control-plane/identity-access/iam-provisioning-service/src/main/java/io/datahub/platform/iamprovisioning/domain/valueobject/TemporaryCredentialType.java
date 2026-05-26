package io.datahub.platform.iamprovisioning.domain.valueobject;

/**
 * Domain-level credential bootstrap types for the initial tenant administrator.
 *
 * <p>This enum deliberately avoids Keycloak field names. Infrastructure adapters
 * translate this business meaning into provider-specific API parameters.
 */
public enum TemporaryCredentialType {

    /**
     * The administrator starts with a temporary password and must replace it
     * before using the account as a normal long-lived credential.
     */
    TEMPORARY_PASSWORD

    /**
     * 预留扩展空间以便未来支持 Magic Link 等方式
     */
}
