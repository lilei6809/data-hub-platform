package io.datahub.platform.iamprovisioning.domain.valueobject;

public enum RealmStrategy {

    /**
     * 所有租户共享一个 Realm，通过 Keycloak Organization 逻辑隔离。
     * MVP 默认策略，适合 Standard/Growth Tier。
     */
    SHARED_REALM,

    /**
     * 租户独占专属 Realm，提供最高级别的 IAM 隔离。
     * 仅允许 Enterprise Tier 及以上。
     */
    DEDICATED_REALM;

    /**
     * First-wave onboarding default: one shared realm with tenant isolation by organization.
     */
    public static final RealmStrategy DEFAULT = SHARED_REALM;
}
