package io.datahub.platform.iamprovisioning.infrastructure.keycloak;

import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakeKeycloakAdminPort implements KeycloakAdminPort {

    // 内存存储：每种对象用最自然的"稳定键"做索引
    // ============================================================

    // Organization：以 TenantId 作为稳定键（业务上一个租户只有一个组织）
    private final Map<TenantId, StoredOrganization> organizations = new ConcurrentHashMap<>();

    // User：以 Email 作为稳定键（邮件地址是用户的唯一业务标识）
    private final Map<Email, StoredUser> users = new ConcurrentHashMap<>();

    // Organization Membership：<OrgId, Set<UserId>>（一个组织有多个成员）
    private final Map<OrganizationId, Set<UserId>> memberships = new ConcurrentHashMap<>();

    // Realm Roles：以 RoleName 去重（角色名在整个 Realm 内唯一）
    private final Set<RealmRoleName> realmRoles = ConcurrentHashMap.newKeySet();

    // User Role Assignments：<UserId, Set<RoleName>>
    private final Map<UserId, Set<RealmRoleName>> userRoleAssignments = new ConcurrentHashMap<>();

    // ============================================================
    // 故障注入机制（测试用）
    // ============================================================
    private final Map<String, Queue<Exception>> scheduledFailures = new ConcurrentHashMap<>();


    // ============================================================
    // ensure 方法实现
    // ============================================================
    @Override
    public OrganizationId ensureOrganization(TenantId tenantId, OrganizationAttributes attributes) {

        // TODO: 故障注入

        return organizations.computeIfAbsent(tenantId,
                id -> {
                    OrganizationId orgId = OrganizationId.of(UUID.randomUUID().toString());

                    return new StoredOrganization(orgId, attributes);
                }).organizationId();
    }

    @Override
    public UserId ensureUser(Email email, TemporaryCredentialPolicy credentialPolicy) {


        return users.computeIfAbsent(email,
                key -> {
                    UserId uid = UserId.of(UUID.randomUUID().toString());

                    return new StoredUser(uid, email, credentialPolicy);
                }).userId();
    }

    @Override
    public void ensureOrganizationMembership(OrganizationId organizationId, UserId userId) {

        // FakeKeycloakAdminPort 的“怎么知道 organizationId 是否存在”：fake adapter 如果要严格模拟真实 Keycloak，可以维护一个 Map<OrganizationId,
        //  StoredOrganization> 或在 organizations.values() 里查。但第一版可以先不做严格校验；等 pipeline 故障注入测试开始后，再让 fake 对不存在的 org/user
        //  抛 KeycloakResourceNotFoundException。
        memberships.computeIfAbsent(organizationId, orgId ->
            ConcurrentHashMap.newKeySet()
        ).add(userId);
    }

    @Override
    public void ensureRealmRole(RealmRoleName realmRoleName) {

        realmRoles.add(realmRoleName);
    }

    @Override
    public void ensureUserRealmRole(UserId userId, RealmRoleName realmRoleName) {

        userRoleAssignments.computeIfAbsent(userId, uid -> ConcurrentHashMap.newKeySet()).add(realmRoleName);
    }

    // 内部存储类（私有 record）
    private record StoredOrganization(OrganizationId organizationId, OrganizationAttributes attributes) {}
    private record StoredUser(UserId userId, Email email, TemporaryCredentialPolicy credentialPolicy) {}
}


