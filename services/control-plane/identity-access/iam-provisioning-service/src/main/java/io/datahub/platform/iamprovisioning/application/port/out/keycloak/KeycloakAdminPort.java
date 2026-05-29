package io.datahub.platform.iamprovisioning.application.port.out.keycloak;

import io.datahub.platform.iamprovisioning.domain.valueobject.*;

public interface KeycloakAdminPort {

    /**
     * 确保给定租户的 Organization 在 Keycloak Realm 中存在。
     *
     * <p>ensure 语义：
     * <ul>
     *   <li>若 Organization 不存在 → 创建并返回新 OrganizationId</li>
     *   <li>若 Organization 已存在 → 直接返回已有 OrganizationId，不重复创建</li>
     *   <li>若属性不一致 → 按约定规则校正（tier 以传入值为准）</li>
     * </ul>
     *
     *
//     * @throws KeycloakTransientException 当 Keycloak 暂时不可用时（可重试）
//     * @throws KeycloakUnauthorizedException 当 Service Account 凭证失效时（不可重试）
     */
    OrganizationId ensureOrganization(TenantId tenantId, OrganizationAttributes attributes);

    /**
     * 确保给定 email 的用户存在。
     * 同一 email 重复调用返回同一 UserId。
     */
    UserId ensureUser(TenantId tenantId, Email email, TemporaryCredentialPolicy credentialPolicy);

    /**
     * 确保用户是 Organization 的成员。
     * 关系已存在时视为成功，不抛异常。
     */
    void ensureOrganizationMembership(TenantId tenantId, OrganizationId organizationId, UserId userId);

    /**
     * 确保 Realm Role 存在。
     * 已存在则跳过，不存在则创建。
     */
    void ensureRealmRole(RealmRoleName realmRoleName);

    /**
     * 确保用户持有指定 Realm Role。
     * 已分配则跳过。
     */
    void ensureUserRealmRole(TenantId tenantId, UserId userId, RealmRoleName realmRoleName);

    // MVP 未实现，Phase 4 扩展（以下方法仅作为命名约定注释）：
    // ensureIdentityProvider(TenantId, IdpConfig)
    // ensureProtocolMapper(ClientId, ProtocolMapperConfig)
    // ensureMfaPolicy(TenantId, MfaPolicyConfig)
    // ensureClientAudience(ClientId, ClientAudienceConfig)
}
