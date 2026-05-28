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

@Slf4j
public class RealKeycloakAdminPort implements KeycloakAdminPort {

    private static final String OP_ENSURE_ORG = "ensureOrganization";
    private static final String ATTR_TENANT_ID = "tenant_id";
    private static final String ATTR_TIER = "tier";

    private final Keycloak keycloak;
    // realm 通过构造器注入（@Value 在 new 出来的对象上不生效，必须由 Configuration 传入）
    private final String realm;

    public RealKeycloakAdminPort(Keycloak keycloak, String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    /**
     * 确保 Keycloak Organization 存在，并校正 tier 属性。
     *
     * <p>幂等语义：
     * <ul>
     *   <li>Organization 不存在 → 创建并返回新 ID（HTTP 201）</li>
     *   <li>Organization 已存在（HTTP 409）→ 按 tenantId 精确搜索，校正 tier 后返回 ID</li>
     * </ul>
     *
     * <p>Organization name 使用 {@code tenantId.value()}，在 Realm 内唯一，是跨调用的稳定键。
     */
    @Override
    public OrganizationId ensureOrganization(TenantId tenantId, OrganizationAttributes attributes) {
        OrganizationRepresentation rep = buildOrgRepresentation(tenantId, attributes);

        try {
            try (Response response = keycloak.realm(realm).organizations().create(rep)) {
                int status = response.getStatus();

                if (status == 201) {
                    String id = extractCreatedId(response);
                    log.atInfo()
                            .addKeyValue("event", "keycloak_org_created")
                            .addKeyValue("tenantId", tenantId)
                            .addKeyValue("organizationId", id)
                            .log("Keycloak Organization created");
                    return OrganizationId.of(id);

                } else if (status == 409) {
                    log.atInfo()
                            .addKeyValue("event", "keycloak_org_already_exists")
                            .addKeyValue("tenantId", tenantId)
                            .log("Organization already exists, reconciling attributes");
                    return findAndReconcile(tenantId, attributes);

                } else if (status == 401 || status == 403) {
                    throw new KeycloakAuthenticationException(tenantId, OP_ENSURE_ORG, "HTTP " + status, null);

                } else if (status >= 400 && status < 500) {
                    throw new KeycloakInvalidRequestException(tenantId, OP_ENSURE_ORG, status, null);

                } else {
                    throw new KeycloakTransientException(OP_ENSURE_ORG, tenantId, null);
                }
            }
        } catch (KeycloakOperationException e) {
            throw e;
        } catch (Exception e) {
            // 网络错误、SDK 内部异常均视为瞬时故障
            throw new KeycloakTransientException(OP_ENSURE_ORG, tenantId, e);
        }
    }

    private OrganizationId findAndReconcile(TenantId tenantId, OrganizationAttributes attributes) {
        // exact=true：只匹配 name 完全等于 tenantId.value() 的 org，避免前缀误匹配
        List<OrganizationRepresentation> found = keycloak.realm(realm).organizations()
                .search(tenantId.value(), true, 0, 1);

        if (found.isEmpty()) {
            // 极少情况：409 之后立刻查不到，可能是短暂不一致，当作瞬时故障触发重试
            throw new KeycloakTransientException(OP_ENSURE_ORG, tenantId,
                    new IllegalStateException("409 Conflict but org not found by name: " + tenantId.value()));
        }

        OrganizationRepresentation existing = found.get(0);
        OrganizationId orgId = OrganizationId.of(existing.getId());
        reconcileAttributes(tenantId, orgId, existing, attributes);
        return orgId;
    }

    /**
     * 属性校正：仅当 tier 或 displayName 发生变化时才调用 update，避免无谓的写操作。
     * tier 以传入的 desired 值为准（幂等写入）。
     */
    private void reconcileAttributes(TenantId tenantId, OrganizationId orgId,
                                     OrganizationRepresentation existing,
                                     OrganizationAttributes desired) {
        Map<String, List<String>> currentAttrs = existing.getAttributes() != null
                ? existing.getAttributes() : new HashMap<>();

        String currentTier = firstAttrValue(currentAttrs, ATTR_TIER);
        String desiredTier = desired.tier().value();
        String currentDisplayName = existing.getDescription();
        String desiredDisplayName = desired.displayName().tenantName();

        boolean tierChanged = !desiredTier.equals(currentTier);
        boolean displayNameChanged = !desiredDisplayName.equals(currentDisplayName);

        if (tierChanged || displayNameChanged) {
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

    private OrganizationRepresentation buildOrgRepresentation(TenantId tenantId, OrganizationAttributes attributes) {
        OrganizationRepresentation rep = new OrganizationRepresentation();
        rep.setName(tenantId.value());                              // Realm 内唯一稳定键
        // description 作为人类可读名称（SDK 26.x 无 displayName 字段）
        rep.setDescription(attributes.displayName().tenantName());
        rep.setEnabled(true);
        rep.setAttributes(buildAttributes(attributes));
        return rep;
    }

    private Map<String, List<String>> buildAttributes(OrganizationAttributes attributes) {
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put(ATTR_TENANT_ID, List.of(attributes.tenantId().value()));
        attrs.put(ATTR_TIER, List.of(attributes.tier().value()));
        return attrs;
    }

    private String extractCreatedId(Response response) {
        String location = response.getHeaderString("Location");
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("Missing Location header in 201 response");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String firstAttrValue(Map<String, List<String>> attrs, String key) {
        List<String> values = attrs.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    // ── 以下方法后续子任务实现 ──

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