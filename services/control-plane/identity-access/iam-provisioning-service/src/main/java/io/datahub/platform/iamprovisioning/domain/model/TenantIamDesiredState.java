package io.datahub.platform.iamprovisioning.domain.model;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;

import java.util.List;

// TenantIamDesiredState 是一个"纯描述性"对象
// 它不执行任何操作，只是描述"我们想要什么"
public record TenantIamDesiredState(
        TenantId tenantId,
        TenantName tenantName,
        Tier tier,
        AdminUser adminUser,
        IdentityMode identityMode,
        RealmStrategy realmStrategy,
        List<IdentityProviderConfig> identityProviders, // MVP 为空列表
        List<PolicyConfig> policies) {
    // compact constructor 执行不变量校验和防御性拷贝
    public TenantIamDesiredState {
        // 必填字段：这三个缺任何一个都无法执行 Provisioning
        requireNonNull(tenantId, "tenantId");
        requireNonNull(tier, "tier");
        requireNonNull(adminUser, "adminUser");

        // 枚举有默认值，但也不允许 null
        requireNonNull(identityMode, "identityMode");
        requireNonNull(realmStrategy, "realmStrategy");
        requireNonNull(identityProviders, "identityProviders");
        requireNonNull(policies, "policies");

        // 跨字段校验：业务规则约束
        // 如果声明要用外部 IdP，那外部 IdP 的配置不能为空
        if (identityMode == IdentityMode.BROKERED_IDP && identityProviders.isEmpty()) {
            throw new DomainValidationException("TenantIamDesiredState",
                    "identityProviders must not be empty when identityMode is BROKERED_IDP");
        }

        // 防御性拷贝：防止调用者在构造后修改传入的 List
        // List.copyOf 同时保证不可变
        identityProviders = List.copyOf(identityProviders);
        policies = List.copyOf(policies);
    }

    // ===== 工厂方法：这是 DDD 的核心实践 =====
    // 工厂方法的价值在于：它表达了一个业务场景（MVP 最小配置）
    // 并且把"默认值是什么"的知识集中在这一个地方
    public static TenantIamDesiredState ofMinimalInput(
            TenantId tenantId,
            TenantName tenantName,
            Tier tier,
            Email adminEmail) {

        return new TenantIamDesiredState(
                tenantId, tenantName,
                tier,   // AdminUser 用默认凭证策略
                AdminUser.initialTenantAdmin(adminEmail),                     // MVP 默认：不用外部 IdP
                IdentityMode.DEFAULT,                     // MVP 默认：共享 Realm
                RealmStrategy.DEFAULT,                                 // MVP：无 IdP 配置
                List.of(),                                 // MVP：无额外策略
                List.of());
    }

    public static TenantIamDesiredState of(
            TenantId tenantId, TenantName tenantName,
            Tier tier,
            Email adminEmail) {

        return new TenantIamDesiredState(
                tenantId, tenantName,
                tier,   // AdminUser 用默认凭证策略
                AdminUser.initialTenantAdmin(adminEmail),                     // MVP 默认：不用外部 IdP
                IdentityMode.DEFAULT,                     // MVP 默认：共享 Realm
                RealmStrategy.DEFAULT,                                 // MVP：无 IdP 配置
                List.of(),                                 // MVP：无额外策略
                List.of());
    }

    @Override
    public String toString() {
        // adminUser 的 toString 已经是安全的（遮蔽 email）
        // 所以这里直接用就是安全的
        return "TenantIamDesiredState{tenantId=%s, tier=%s, adminUser=%s, mode=%s, strategy=%s}"
                .formatted(tenantId, tier, adminUser, identityMode, realmStrategy);
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException("TenantIamDesiredState", fieldName + " must not be null");
        }
    }
}
