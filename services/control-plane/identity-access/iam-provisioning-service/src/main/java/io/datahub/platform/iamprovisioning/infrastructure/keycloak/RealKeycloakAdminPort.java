package io.datahub.platform.iamprovisioning.infrastructure.keycloak;

import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakAuthenticationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakInvalidRequestException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakOperationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakTransientException;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.util.SecureRandomPasswordGenerator;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实 Keycloak Admin API 适配器（Outbound Adapter）。
 *
 * <h3>架构定位</h3>
 * <p>本类实现 {@link KeycloakAdminPort}（Driven Port），是应用核心与 Keycloak 之间的唯一技术边界。
 * 应用核心（Step Pipeline）只依赖 Port 接口，不感知任何 SDK 细节。</p>
 *
 * <h3>核心约定</h3>
 * <ul>
 *   <li><b>ensure 语义</b>：所有操作均幂等——已存在则复用，不抛异常，不重复创建。</li>
 *   <li><b>异常边界</b>：任何 Keycloak SDK 异常必须在本类边界内被捕获并翻译为领域异常；
 *       SDK 类型绝不泄漏到 application 层。</li>
 *   <li><b>409 吸收</b>：创建时遇到 409 Conflict 不是错误，而是"已存在"的信号，
 *       需回退查询并校正属性后正常返回。</li>
 *   <li><b>禁止事务包裹</b>：Keycloak 无分布式事务支持，所有调用均为独立 HTTP 请求，
 *       依赖幂等性而非事务回滚。</li>
 * </ul>
 */
@Slf4j
public class RealKeycloakAdminPort implements KeycloakAdminPort {

    // 操作名称常量：用于异常构造和日志，便于排查"哪个操作失败了"
    private static final String OP_ENSURE_ORG = "ensureOrganization";

    // Organization attributes 的 key 名称：与下游消费方（tier 缓存、审计）约定一致
    private static final String ATTR_TENANT_ID = "tenant_id";
    private static final String ATTR_TIER = "tier";

    private final Keycloak keycloak;

    // realm 必须通过构造器注入：
    // 本类由 KeycloakAdapterConfiguration 用 new 创建，不是 Spring 管理的 Bean，
    // 因此 @Value/@Autowired 字段注入均不生效。
    private final String realm;

    public RealKeycloakAdminPort(Keycloak keycloak, String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    // ══════════════════════════════════════════════════════════════════
    // ensureOrganization
    // ══════════════════════════════════════════════════════════════════

    /**
     * 确保 Keycloak Organization 存在，并校正 tier 属性。
     *
     * <h4>执行流程</h4>
     * <pre>
     * 1. 调用 Keycloak 创建 Organization
     *    ├── 201 Created  → 从 Location header 提取 ID，直接返回
     *    ├── 409 Conflict → Organization 已存在，进入"查询 + 校正"分支
     *    ├── 401/403      → Service Account 凭证问题，抛不可重试异常
     *    ├── 其他 4xx     → 请求参数非法，抛不可重试异常
     *    └── 5xx / 网络  → 瞬时故障，抛可重试异常
     * 2. （仅 409 分支）精确搜索已有 Organization，校正 tier 和 description 后返回 ID
     * </pre>
     *
     * <h4>Organization name 的稳定键设计</h4>
     * <p>使用 {@code tenantId.value()} 作为 Organization 的 {@code name}。
     * {@code name} 在 Realm 内唯一且不可变，是跨调用幂等查询的可靠依据。</p>
     */
    @Override
    public OrganizationId ensureOrganization(TenantId tenantId, OrganizationAttributes attributes) {
        OrganizationRepresentation rep = buildOrgRepresentation(tenantId, attributes);

        try {
            // try-with-resources 确保 Response 资源被关闭，避免连接泄漏
            try (Response response = keycloak.realm(realm).organizations().create(rep)) {
                int status = response.getStatus();

                if (status == 201) {
                    // 创建成功：从 Location header 中提取新 Organization 的 ID
                    // Location 格式：.../organizations/{id}
                    String id = extractCreatedId(response);
                    log.atInfo()
                            .addKeyValue("event", "keycloak_org_created")
                            .addKeyValue("tenantId", tenantId)
                            .addKeyValue("organizationId", id)
                            .log("Keycloak Organization created");
                    return OrganizationId.of(id);

                } else if (status == 409) {
                    // Organization 已存在：不是错误，进入"查询 + 属性校正"分支
                    // 典型场景：Provisioning 重试、手动补跑
                    log.atInfo()
                            .addKeyValue("event", "keycloak_org_already_exists")
                            .addKeyValue("tenantId", tenantId)
                            .log("Organization already exists, reconciling attributes");
                    return findAndReconcile(tenantId, attributes);

                } else if (status == 401 || status == 403) {
                    // Service Account 凭证失效或权限不足，重试无意义，需运维介入
                    throw new KeycloakAuthenticationException(tenantId, KeycloakOperation.ENSURE_ORGANIZATION, "HTTP " + status, null);

                } else if (status >= 400 && status < 500) {
                    // 其他 4xx：请求本身有问题（如字段非法），重试不会解决
                    String body = response.readEntity(String.class);
                    log.atError()
                            .addKeyValue("event", "keycloak_org_create_rejected")
                            .addKeyValue("tenantId", tenantId)
                            .addKeyValue("httpStatus", status)
                            .addKeyValue("responseBody", body)
                            .log("Keycloak rejected organization creation request");
                    throw new KeycloakInvalidRequestException(tenantId, KeycloakOperation.ENSURE_ORGANIZATION, status, null);

                } else {
                    // 5xx：Keycloak 服务端错误，视为瞬时故障，值得重试
                    throw new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION, tenantId, null);
                }
            }
        } catch (KeycloakOperationException e) {
            // 领域异常直接透传，不重复包装
            throw e;
        }

        catch (Exception e) {


            if (hasAuthFailureInCause(e)){
                throw new KeycloakAuthenticationException(tenantId, KeycloakOperation.ENSURE_ORGANIZATION,
                        "Token acquisition failed", e);
            }

            // 网络超时、连接拒绝、SDK 内部异常等均视为瞬时故障
            // 保留原始 cause，确保排查时能看到完整堆栈
            throw new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION, tenantId, e);
        }
    }

    private boolean hasAuthFailureInCause(Throwable e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof NotAuthorizedException || cause instanceof ForbiddenException) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * 409 分支：精确搜索已有 Organization，校正属性后返回 ID。
     *
     * <p>之所以用 exact=true 精确匹配，是因为 Keycloak 的模糊搜索可能返回名称含
     * tenantId 作为前缀的其他 Organization，导致误操作。</p>
     */
    private OrganizationId findAndReconcile(TenantId tenantId, OrganizationAttributes attributes) {
        // exact=true：仅匹配 name 完全等于 tenantId.value() 的 Organization
        List<OrganizationRepresentation> found = keycloak.realm(realm).organizations()
                .search(tenantId.value(), true, 0, 1);

        if (found.isEmpty()) {
            // 极少数情况：刚刚收到 409，但立刻搜不到。
            // 可能原因：Keycloak 内部短暂不一致（如写入后索引未刷新）。
            // 处理：当作瞬时故障抛出，让 Pipeline 重试，下次搜索应能找到。
            throw new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION, tenantId,
                    new IllegalStateException("409 Conflict but org not found by name: " + tenantId.value()));
        }

        OrganizationRepresentation existing = found.get(0);
        OrganizationId orgId = OrganizationId.of(existing.getId());

        // 校正属性：确保 tier 和 description 与期望状态一致
        reconcileAttributes(tenantId, orgId, existing, attributes);
        return orgId;
    }

    /**
     * 属性校正（Reconcile）：比较当前状态与期望状态，仅在有差异时才发送 update 请求。
     *
     * <h4>校正规则</h4>
     * <ul>
     *   <li>{@code tier}：以传入的 desired 值为准（幂等覆盖）</li>
     *   <li>{@code description}（即 tenantName）：以传入的 desired 值为准</li>
     *   <li>无变化时跳过 update 调用，避免对 Keycloak 产生不必要的写压力</li>
     * </ul>
     *
     * <h4>为什么用 description 存 tenantName</h4>
     * <p>Keycloak SDK 26.x 的 {@code OrganizationRepresentation} 没有 {@code displayName} 字段，
     * {@code description} 是最接近"人类可读名称"的字段。</p>
     */
    private void reconcileAttributes(TenantId tenantId, OrganizationId orgId,
                                     OrganizationRepresentation existing,
                                     OrganizationAttributes desired) {
        // 获取 Keycloak 中的当前属性，null 安全处理
        Map<String, List<String>> currentAttrs = existing.getAttributes() != null
                ? existing.getAttributes() : new HashMap<>();

        String currentTier = firstAttrValue(currentAttrs, ATTR_TIER);
        String desiredTier = desired.tier().value();

        // description 对应 tenantName（参见方法 JavaDoc 中的说明）
        String currentDisplayName = existing.getDescription();
        String desiredDisplayName = desired.displayName().tenantName();

        boolean tierChanged = !desiredTier.equals(currentTier);
        boolean displayNameChanged = !desiredDisplayName.equals(currentDisplayName);

        if (tierChanged || displayNameChanged) {
            // 在 existing 对象上直接修改后提交，保留 Keycloak 端其他未知字段（如 domains）
            existing.setDescription(desiredDisplayName);
            existing.setAttributes(buildAttributes(desired));
            keycloak.realm(realm).organizations().get(orgId.value()).update(existing);

            log.atInfo()
                    .addKeyValue("event", "keycloak_org_attributes_reconciled")
                    .addKeyValue("tenantId", tenantId)
                    .addKeyValue("organizationId", orgId)
                    .addKeyValue("tierChanged", tierChanged)
                    .addKeyValue("displayNameChanged", displayNameChanged)
                    .log("Keycloak Organization attributes reconciled");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 私有辅助方法
    // ══════════════════════════════════════════════════════════════════

    /**
     * 构造 Keycloak OrganizationRepresentation。
     *
     * <p>字段映射：
     * <ul>
     *   <li>{@code name} = tenantId.value()：Realm 内唯一稳定键，不可变</li>
     *   <li>{@code description} = tenantName：人类可读名称</li>
     *   <li>{@code attributes}：业务元数据（tenant_id、tier），供下游消费</li>
     * </ul>
     */
    private OrganizationRepresentation buildOrgRepresentation(TenantId tenantId, OrganizationAttributes attributes) {
        OrganizationRepresentation rep = new OrganizationRepresentation();
        rep.setName(tenantId.value());
        rep.setDescription(attributes.displayName().tenantName());
        rep.setEnabled(true);
        rep.setAttributes(buildAttributes(attributes));
        // Keycloak 要求至少一个 domain，使用 tenantId 衍生内部域名
        //   MVP 阶段不需要邮件域路由，但 Keycloak schema 强制要求至少一个 domain。我们用 tenant-acme.internal 纯粹是为了通过校验。
        // tenant-acme.internal → .internal 是 RFC 保留的非公网域名，Keycloak 接受，但不会和任何真实邮件流量冲突
        rep.addDomain(new OrganizationDomainRepresentation(tenantId.value() + ".internal"));
        return rep;
    }

    /**
     * 构造 Organization 的 attributes map。
     *
     * <p>Keycloak attributes 格式为 {@code Map<String, List<String>>}（支持多值），
     * 此处每个 key 只存单值。</p>
     */
    private Map<String, List<String>> buildAttributes(OrganizationAttributes attributes) {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put(ATTR_TENANT_ID, List.of(attributes.tenantId().value()));
        attrs.put(ATTR_TIER, List.of(attributes.tier().value()));
        return attrs;
    }

    /**
     * 从 201 Created 响应的 Location header 中提取资源 ID。
     *
     * <p>Keycloak 创建资源后返回的 Location 格式固定为：
     * {@code .../admin/realms/{realm}/organizations/{id}}</p>
     */
    private String extractCreatedId(Response response) {
        String location = response.getHeaderString("Location");
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Missing Location header in 201 response");
        }
        // 取最后一个 '/' 之后的部分即为 UUID
        return location.substring(location.lastIndexOf('/') + 1);
    }

    /**
     * 安全地取 attributes map 中某个 key 的第一个值。
     * Keycloak attributes 是多值的，业务上我们只写单值，取第一个即可。
     */
    private String firstAttrValue(Map<String, List<String>> attrs, String key) {
        List<String> values = attrs.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }



    @Override
    public UserId ensureUser(TenantId tenantId, Email email, TemporaryCredentialPolicy credentialPolicy) {
        try {
            UserId existingUserId = findUserByEmail(email);
            if (existingUserId != null) {
                return existingUserId;
            }

            UserRepresentation userRep = buildUserRepresentation(email, credentialPolicy);

            try (Response response = keycloak.realm(realm).users().create(userRep)) {
                int status = response.getStatus();

                if (status == 201) {
                    UserId userId = UserId.of(extractCreatedId(response));
                    log.atInfo()
                            .addKeyValue("event", "keycloak_user_created")
                            .addKeyValue("tenantId", tenantId)
                            .addKeyValue("userId", userId)
                            .log("Keycloak user created");
                    return userId;
                }

                if (status == 409) {
                    log.atInfo()
                            .addKeyValue("event", "keycloak_user_already_exists")
                            .addKeyValue("tenantId", tenantId)
                            .addKeyValue("email", email)
                            .log("User already exists, resolving existing userId");
                    return findUserByEmailAfterConflict(tenantId, email);
                }

                if (status == 401 || status == 403) {
                    throw new KeycloakAuthenticationException(tenantId, KeycloakOperation.ENSURE_USER, "HTTP " + status, null);
                }

                if (status >= 400 && status < 500) {
                    String body = readResponseBody(response);
                    log.atError()
                            .addKeyValue("event", "keycloak_user_create_rejected")
                            .addKeyValue("tenantId", tenantId)
                            .addKeyValue("httpStatus", status)
                            .addKeyValue("responseBody", body)
                            .log("Keycloak rejected user creation request");
                    throw new KeycloakInvalidRequestException(tenantId, KeycloakOperation.ENSURE_USER, status, null);
                }

                throw new KeycloakTransientException(KeycloakOperation.ENSURE_USER, tenantId, null);
            }
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            if (hasAuthFailureInCause(e)) {
                throw new KeycloakAuthenticationException(
                        tenantId,
                        KeycloakOperation.ENSURE_USER,
                        "Token acquisition failed",
                        e
                );
            }
            throw new KeycloakTransientException(KeycloakOperation.ENSURE_USER, tenantId, e);
        }
    }

    private UserId findUserByEmailAfterConflict(TenantId tenantId, Email email) {
        UserId userId = findUserByEmail(email);
        if (userId == null) {
            throw new KeycloakTransientException(
                    KeycloakOperation.ENSURE_USER,
                    tenantId,
                    new IllegalStateException("409 Conflict but user not found by email: " + email.value())
            );
        }
        return userId;
    }

    private UserId findUserByEmail(Email email) {
        List<UserRepresentation> existing = keycloak.realm(realm).users().searchByEmail(email.value(), true);
        if (existing.isEmpty()) {
            return null;
        }
        return userIdFrom(existing.get(0), email.value());
    }

    private UserId userIdFrom(UserRepresentation user, String lookupValue) {
        String id = user.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Keycloak user missing id for lookup: " + lookupValue);
        }
        return UserId.of(id);
    }

    private UserRepresentation buildUserRepresentation(Email email, TemporaryCredentialPolicy credentialPolicy) {
        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername(email.value());
        userRep.setEmail(email.value());
        userRep.setEnabled(true);
        userRep.setEmailVerified(false);
        userRep.setRequiredActions(credentialPolicy.requiredActions().stream()
                .map(Enum::name)
                .toList());

        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        String psw = SecureRandomPasswordGenerator.generateSecureRandomPassword();
        log.info("密码已创建: {}", psw);
        credentialRepresentation.setValue(psw);
        credentialRepresentation.setTemporary(true);
        userRep.setCredentials(List.of(credentialRepresentation));
        return userRep;
    }

    private String readResponseBody(Response response) {
        try {
            return response.readEntity(String.class);
        } catch (Exception e) {
            return "<unreadable response body>";
        }
    }

    private String generateTemporaryPassword() {
        // 使用 SecureRandom 生成符合密码策略的临时密码
        // 生产环境通常要求：大小写 + 数字 + 特殊字符 + 最小长度
        return SecureRandomPasswordGenerator.generateSecureRandomPassword();

        // 这个值只活在内存里，用于 POST /users，然后消失
        // 绝不能：log.info("Generated temp password: {}", password)
        // 绝不能：放入任何 DTO 返回给上层
        // 绝不能：存入数据库
    }


    // ══════════════════════════════════════════════════════════════════
    // 待实现方法（后续子任务）
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void ensureOrganizationMembership(TenantId tenantId, OrganizationId organizationId, UserId userId) {
        // Step 1: 检查 membership 是否已存在
        // 前置检查（org/user 是否存在）由 Step Pipeline 保证：
        // EnsureOrganizationStep 和 EnsureAdminUserStep 均已在此步骤之前成功执行。
        // GET /realms/cdp/organizations/{orgId}/members/{userId}
        if (membershipExists(organizationId, userId)){
            // 存在直接返回
            return;
        }

        // Step 2: 添加 membership
        //    // POST /realms/cdp/organizations/{orgId}/members
        try (Response response = createMembership(organizationId, userId)){
            int status = response.getStatus();

            if (status == 201) {
                log.atInfo()
                        .addKeyValue("event", "keycloak_membership_created")
                        .addKeyValue("tenantId", tenantId)
                        .addKeyValue("organizationId", organizationId)
                        .addKeyValue("userId", userId)
                        .log("Keycloak membership created");
                return;
            }

            if (status == 409) {
                log.atInfo()
                        .addKeyValue("event", "keycloak_membership_existed")
                        .addKeyValue("tenantId", tenantId)
                        .addKeyValue("organizationId", organizationId)
                        .addKeyValue("userId", userId)
                        .log("Keycloak membership existed");
                return;
            }



            if (status == 401 || status == 403) {
                throw new KeycloakAuthenticationException(tenantId, KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, "HTTP " + status, null);
            }

            if (status >= 400 && status < 500) {
                String body = readResponseBody(response);
                log.atError()
                        .addKeyValue("event", "keycloak_membership_create_rejected")
                        .addKeyValue("tenantId", tenantId)
                        .addKeyValue("httpStatus", status)
                        .addKeyValue("responseBody", body)
                        .log("Keycloak rejected membership creation request");
                throw new KeycloakInvalidRequestException(tenantId, KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, status, null);
            }

            throw new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,tenantId,null);

        }

        catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            if (hasAuthFailureInCause(e)) {
                throw new KeycloakAuthenticationException(
                        tenantId,
                        KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP,
                        "Token acquisition failed",
                        e
                );
            }
            throw new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, tenantId, e);
        }
    }

    private Response createMembership(OrganizationId organizationId, UserId userId) {
        return keycloak.realm(realm).organizations().get(organizationId.value())
                .members().addMember(userId.value());
    }

    private boolean membershipExists(OrganizationId organizationId, UserId userId) {
        try {
            keycloak.realm(realm)
                    .organizations()
                    .get(organizationId.value())
                    .members()
                    .member(userId.value())
                    .toRepresentation();
            return true;
        } catch (NotFoundException e){
            return false;
        }
    }

    @Override
    public void ensureRealmRole(RealmRoleName realmRoleName) {
        try {
            try {
                keycloak.realm(realm)
                        .roles()
                        .get(realmRoleName.roleName())
                        .toRepresentation();
                return;
            } catch (NotFoundException ignored) {
                // Role 不存在才进入创建分支；ensure 语义下已存在直接成功。
            }

            RoleRepresentation roleRepresentation = new RoleRepresentation();
            roleRepresentation.setName(realmRoleName.roleName());
            keycloak.realm(realm).roles().create(roleRepresentation);

            log.atInfo()
                    .addKeyValue("event", "keycloak_realm_role_created")
                    .addKeyValue("realmRoleName", realmRoleName.roleName())
                    .log("Keycloak realm role created");
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            if (hasAuthFailureInCause(e)) {
                throw new KeycloakAuthenticationException(
                        null,
                        KeycloakOperation.ENSURE_REALM_ROLE,
                        "Token acquisition failed",
                        e
                );
            }
            throw new KeycloakTransientException(KeycloakOperation.ENSURE_REALM_ROLE, null, e);
        }
    }

    @Override
    public void ensureUserRealmRole(TenantId tenantId, UserId userId, RealmRoleName realmRoleName) {
        try{
            List<RoleRepresentation> roles = findRolesByUserId(userId);

            boolean match = roles.stream().anyMatch(
                    role -> role.getName().equals(realmRoleName.roleName())
            );

            if (match){
                log.atInfo()
                        .addKeyValue("event", "keycloak_realm-role-attach_existed")
                        .addKeyValue("tenantId", tenantId)
                        .addKeyValue("userId", userId)
                        .addKeyValue("realmRoleName", realmRoleName.roleName())
                        .log("Keycloak realm-role attaching existed");
                return;
            }

            attachRole(userId, realmRoleName);

            log.atInfo()
                    .addKeyValue("event", "keycloak_realm-role_attached")
                    .addKeyValue("tenantId", tenantId)
                    .addKeyValue("userId", userId)
                    .addKeyValue("realmRoleName", realmRoleName.roleName())
                    .log("Keycloak realm-role attached");
        }
        catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            if (hasAuthFailureInCause(e)) {
                throw new KeycloakAuthenticationException(
                        tenantId,
                        KeycloakOperation.ENSURE_USER_REALM_ROLE,
                        "Token acquisition failed",
                        e
                );
            }
            throw new KeycloakTransientException(KeycloakOperation.ENSURE_USER_REALM_ROLE, tenantId, e);
        }
    }

    private void attachRole(UserId userId, RealmRoleName realmRoleName) {
        RoleRepresentation roleRep = keycloak.realm(realm)
                .roles()
                .get(realmRoleName.roleName())
                .toRepresentation();

        keycloak.realm(realm)
                .users()
                .get(userId.value())
                .roles()
                .realmLevel()
                .add(List.of(roleRep));

    }

    private List<RoleRepresentation> findRolesByUserId(UserId userId) {
        return keycloak.realm(realm).users().get(userId.value())
                .roles().realmLevel()
                .listAll();
    }
}
