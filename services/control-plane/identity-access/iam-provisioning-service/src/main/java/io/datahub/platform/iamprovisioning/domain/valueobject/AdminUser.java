package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;

/**
 * Minimal domain facts required to provision the initial tenant administrator.
 *
 * <p>The tenant, organization membership, and TENANT_ADMIN role assignment are
 * desired-state concerns. This value object only describes the user to create
 * and the credential bootstrap policy to apply.
 *
 * AdminUser 组合了 Email 和策略，表达 "一个有初始凭证策略的管理员"
 */
public record AdminUser(
        Email email,
        TemporaryCredentialPolicy temporaryCredentialPolicy
) {

    public AdminUser {
        if (email == null) {
            throw new DomainValidationException("AdminUser", "email must not be null");
        }
        if (temporaryCredentialPolicy == null) {
            throw new DomainValidationException("AdminUser", "temporary credential policy must not be null");
        }
    }

    public static AdminUser of(Email email, TemporaryCredentialPolicy temporaryCredentialPolicy) {
        return new AdminUser(email, temporaryCredentialPolicy);
    }

    /**
     * Convenience factory for the current onboarding default.
     *
     * <p>Future onboarding flows can add separate factories without changing
     * the AdminUser shape, for example invitation link or external IdP flows.
     */
    public static AdminUser initialTenantAdmin(Email email) {
        return new AdminUser(
                email,
                TemporaryCredentialPolicy.temporaryPasswordWithRequiredPasswordUpdate()
        );
    }

    @Override
    public String toString() {
        return "AdminUser[email=%s, temporaryCredentialPolicy=%s]"
                .formatted(email, temporaryCredentialPolicy); // // Email 的 toString 已经是遮蔽的，所以这里是安全的
    }
}
