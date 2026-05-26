package io.datahub.platform.iamprovisioning.domain.valueobject;

// 枚举在 DDD 里也是值对象的一种形式
// 关键是给每个值加上业务含义的注释，让代码自带文档

/**
 * **`IdentityMode` 回答的问题是：这个租户的用户怎么登录？**
 *
 * - **`LOCAL_ONLY`：用户直接在我们平台的 Keycloak Shared Realm 里注册和登录，MVP 默认值。**
 * - **`BROKERED_IDP`：这个租户有自己的企业级身份提供商（比如 Okta、Azure AD），他们的员工通过 SAML/OIDC 单点登录到我们的平台（BYO IdP 场景）。**
 */
public enum IdentityMode {

    /**
     * 用户通过平台 Shared Realm 直接注册登录。
     * MVP 默认模式，无需额外 IdP 配置。
     */
    LOCAL_ONLY,

    /**
     * 租户通过外部 IdP（OIDC/SAML）经由 Keycloak Brokering 登录。
     * 需要 identityProviders 字段非空配置。
     */
    BROKERED_IDP;

    /**
     * First-wave onboarding default: users authenticate locally in the shared realm.
     */
    public static final IdentityMode DEFAULT = LOCAL_ONLY;
}
