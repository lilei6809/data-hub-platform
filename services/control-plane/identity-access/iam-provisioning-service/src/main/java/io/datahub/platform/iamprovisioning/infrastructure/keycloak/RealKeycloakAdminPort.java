package io.datahub.platform.iamprovisioning.infrastructure.keycloak;

import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakAuthenticationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakInvalidRequestException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakOperationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakTransientException;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.OrganizationRepresentation;

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
                    throw new KeycloakAuthenticationException(tenantId, OP_ENSURE_ORG, "HTTP " + status, null);

                } else if (status >= 400 && status < 500) {
                    // 其他 4xx：请求本身有问题（如字段非法），重试不会解决
                    throw new KeycloakInvalidRequestException(tenantId, OP_ENSURE_ORG, status, null);

                } else {
                    // 5xx：Keycloak 服务端错误，视为瞬时故障，值得重试
                    throw new KeycloakTransientException(OP_ENSURE_ORG, tenantId, null);
                }
            }
        } catch (KeycloakOperationException e) {
            // 领域异常直接透传，不重复包装
            throw e;
        } catch (Exception e) {
            // 网络超时、连接拒绝、SDK 内部异常等均视为瞬时故障
            // 保留原始 cause，确保排查时能看到完整堆栈
            throw new KeycloakTransientException(OP_ENSURE_ORG, tenantId, e);
        }
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
            throw new KeycloakTransientException(OP_ENSURE_ORG, tenantId,
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

    // ══════════════════════════════════════════════════════════════════
    // 待实现方法（后续子任务）
    // ══════════════════════════════════════════════════════════════════

    @Override
    public UserId ensureUser(Email email, TemporaryCredentialPolicy credentialPolicy) {
        throw new UnsupportedOperationException("Not implemented yet (LEI-104)");
    }

    @Override
    public void ensureOrganizationMembership(OrganizationId organizationId, UserId userId) {
        throw new UnsupportedOperationException("Not implemented yet (LEI-115)");
    }

    @Override
    public void ensureRealmRole(RealmRoleName realmRoleName) {
        throw new UnsupportedOperationException("Not implemented yet (LEI-167)");
    }

    @Override
    public void ensureUserRealmRole(UserId userId, RealmRoleName realmRoleName) {
        throw new UnsupportedOperationException("Not implemented yet (LEI-167)");
    }
}