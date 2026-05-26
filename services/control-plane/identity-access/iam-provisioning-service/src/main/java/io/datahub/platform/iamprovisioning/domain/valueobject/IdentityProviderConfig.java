package io.datahub.platform.iamprovisioning.domain.valueobject;

/**
 * LEI-161 引入 IdentityProviderConfig 和 PolicyConfig 这两个扩展占位类型。"占位"的意思是：MVP 阶段这两个字段是空列表，但它们的类型已经被定义好了，未来实现 BYO IdP 时只需要填充内容，而不需要改变 TenantIamDesiredState 的结构
 */
public record IdentityProviderConfig() {
}

//public record IdentityProviderConfig(
//        String alias,              // Keycloak 里的别名，如 "okta-sso"
//        ProviderType providerType, // OIDC 还是 SAML
//        String displayName,        // 显示给用户的名字
//        Map<String, String> configMap, // 非敏感配置参数
//        String secretRef           // 指向 SecretStore 的引用，不是明文 secret
//) {
//    // 关键约束：secretRef 不是真实的 secret 值，
//    // 而是类似 "vault:secret/tenants/acme/idp-client-secret" 的引用路径
//    // 真实 secret 由 SecretStorePort 在运行时解析
//    public IdentityProviderConfig {
//        Objects.requireNonNull(alias, "alias must not be null");
//        Objects.requireNonNull(providerType, "providerType must not be null");
//    }
//}
//
//public enum ProviderType { OIDC, SAML }
