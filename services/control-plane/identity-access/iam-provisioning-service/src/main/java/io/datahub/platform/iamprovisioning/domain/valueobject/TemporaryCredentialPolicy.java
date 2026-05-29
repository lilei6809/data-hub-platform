package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;

import java.util.Objects;
import java.util.Set;

/**
 * Describes how the initial tenant administrator receives the first login credential.
 *  为什么要这样设计？因为领域层不应该生成真实密码，真实密码的生成属于基础设施细节（可能来自 Vault、随机生成器等）。
 *  领域层只负责表达"这个用户的初始凭证应该遵循什么规则"。
 * <p>This value object does not hold a password. It only captures the desired
 * provisioning semantics: what kind of temporary credential is needed and which
 * actions the administrator must complete on first login.
 */
public record TemporaryCredentialPolicy(
        TemporaryCredentialType credentialType,   // 临时凭证类型
        Set<RequiredLoginAction> requiredActions  // adminUser首次登录必须完成的行为: 如修改初始密码
) {

    /**
     * First-wave policy for tenant onboarding:
     * temporary password plus mandatory password replacement on first login.
     */
    public static TemporaryCredentialPolicy temporaryPasswordWithRequiredPasswordUpdate() {
        return new TemporaryCredentialPolicy(
                // 首次登录使用 临时密码
                TemporaryCredentialType.TEMPORARY_PASSWORD,
                // 登录后必须更新密码
                Set.of(RequiredLoginAction.UPDATE_PASSWORD)
        );
    }

    public static TemporaryCredentialPolicy defaultPolicy() {
        return temporaryPasswordWithRequiredPasswordUpdate();
    }

    public TemporaryCredentialPolicy {
        if (credentialType == null) {
            throw new DomainValidationException("TemporaryCredentialPolicy", "credential type must not be null");
        }
        if (requiredActions == null) {
            throw new DomainValidationException("TemporaryCredentialPolicy", "required actions must not be null");
        }

        if (requiredActions.stream().anyMatch(Objects::isNull)) {
            throw new DomainValidationException("TemporaryCredentialPolicy", "required actions must not contain null");
        }
        requiredActions = Set.copyOf(requiredActions);

        if (credentialType == TemporaryCredentialType.TEMPORARY_PASSWORD
                && !requiredActions.contains(RequiredLoginAction.UPDATE_PASSWORD)) {
            throw new DomainValidationException(
                    "TemporaryCredentialPolicy",
                    "temporary password must require UPDATE_PASSWORD on first login"
            );
        }
    }

    @Override
    public String toString() {
        return "TemporaryCredentialPolicy[credentialType=%s, requiredActions=%s]"
                .formatted(credentialType, requiredActions);
    }
}
