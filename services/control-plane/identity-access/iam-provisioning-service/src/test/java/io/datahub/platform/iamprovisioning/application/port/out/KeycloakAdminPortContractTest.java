package io.datahub.platform.iamprovisioning.application.port.out;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.KeycloakAdminPort;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.FakeKeycloakAdminPort;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.RealKeycloakAdminPortAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.*;

abstract class KeycloakAdminPortContractTest {

    protected abstract KeycloakAdminPort createPort();

    KeycloakAdminPort port;

    @BeforeEach
    void setup() {
        port = createPort();
    }

    // ensureOrganization
    // ---- 契约 1: ensure 语义 —— 第一次调用必须创建 ----
    @Test
    @DisplayName("ensureOrganization: 首次调用应创建并返回一个 OrganizationId")
    void ensureOrganization_firstCall_shouldCreate() {
        TenantId tenantId = TenantId.of("tenant-aaa");
        OrganizationAttributes orgAttributes = OrganizationAttributes.from(
                TenantIamDesiredState.ofMinimalInput(
                        tenantId,
                        TenantName.of("acme"),
                        Tier.of("BASIC"),
                        Email.of("jane.admin@example.com")

                )
        );


        OrganizationId organizationId = port.ensureOrganization(tenantId, orgAttributes);

        assertThat(organizationId).isNotNull();
    }

    // ---- 契约 2: 幂等语义 —— 重复调用必须返回同一个 ID ----
    @Test
    @DisplayName("ensureOrganization: 重复调用应返回相同 OrganizationId，不创建新对象")
    void ensureOrganization_secondCall_shouldReturnSameId() {
        TenantId tenantId = TenantId.of("tenant-aaa");
        OrganizationAttributes orgAttributes = OrganizationAttributes.from(
                TenantIamDesiredState.ofMinimalInput(
                        tenantId,
                        TenantName.of("acme"),
                        Tier.of("BASIC"),
                        Email.of("jane.admin@example.com")

                )
        );

        OrganizationId firstId = port.ensureOrganization(tenantId, orgAttributes);

        // When: 再调用一次，参数完全相同
        OrganizationId secondId = port.ensureOrganization(tenantId, orgAttributes);

        assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    @DisplayName("乱序幂等：多次调用返回稳定结果")
    void ensureOrganization_multipleCallsOutOfOrder_shouldBeStable() {
        TenantId tenantId = TenantId.of("tenant-stable");
        OrganizationAttributes attrs = new OrganizationAttributes(tenantId, TenantName.of("abc"), Tier.of("BASIC"));

        // 调用 5 次，所有结果应该相同
        Set<OrganizationId> results = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            results.add(port.ensureOrganization(tenantId, attrs));
        }

        assertThat(results.size()).isEqualTo(1);
    }



    // ensureUser
    // 首次调用
    @Test
    @DisplayName("ensureUser: 首次调用时应创建并返回一个 userId")
    void ensureUser_firstCall_shouldCreate() {
        //given
        TenantId tenantId = TenantId.of("tenant-user");
        Email email = Email.of("abc@abc.com");
        TemporaryCredentialPolicy credentialPolicy = TemporaryCredentialPolicy.defaultPolicy();

        UserId userId = port.ensureUser(tenantId, email, credentialPolicy);

        assertThat(userId).isNotNull();
    }

    @Test
    @DisplayName("ensureUser: 重复调用应返回相同 userId，不创建新对象")
    void ensureUser_secondCall_shouldReturnSameId() {
        //given
        TenantId tenantId = TenantId.of("tenant-user");
        Email email = Email.of("abc@abc.com");
        TemporaryCredentialPolicy credentialPolicy = TemporaryCredentialPolicy.defaultPolicy();

        UserId firstId = port.ensureUser(tenantId, email, credentialPolicy);
        UserId secondId = port.ensureUser(tenantId, email, credentialPolicy);

        assertThat(firstId).isEqualTo(secondId);
    }


    @Test
    @DisplayName("ensureUser:  不同的 email 代表不同的 user, 应该返回不同的 userId")
    void ensureUser_diffEmail_shouldReturnDiffId() {
        //given
        TenantId tenantId = TenantId.of("tenant-user");
        Email email1 = Email.of("abc@abc.com");
        Email email2 = Email.of("123@123.com");
        TemporaryCredentialPolicy credentialPolicy = TemporaryCredentialPolicy.defaultPolicy();

        UserId firstId = port.ensureUser(tenantId, email1, credentialPolicy);
        UserId secondId = port.ensureUser(tenantId, email2, credentialPolicy);

        assertThat(firstId).isNotEqualTo(secondId);
    }

    // ensureOrganizationMembership
    @Test
    @DisplayName("ensureOrganizationMembership：关系已存在时不应抛出异常")
    void ensureOrganizationMembership_whenAlreadyMember_shouldNotThrow() {
        // 准备
        TenantId tenantId = TenantId.of("tenant-member");
        OrganizationId orgId = port.ensureOrganization(tenantId,
                new OrganizationAttributes(tenantId, TenantName.of("abc"), Tier.of("BASIC")));
        UserId userId = port.ensureUser(
                tenantId,
                Email.of("admin@tenant-member.com"),
                TemporaryCredentialPolicy.defaultPolicy());

        // 第一次：建立关系
        port.ensureOrganizationMembership(tenantId, orgId, userId);

        // 第二次：重复调用不应抛出任何异常
        assertThatNoException().isThrownBy(
                () -> port.ensureOrganizationMembership(tenantId, orgId, userId)
        );
    }

    // ensureRealmRole
    // 语义是：
    //  - Role 不存在则创建
    //  - Role 已存在则成功
    //  - 不暴露 409 conflict 给 application service
    @Test
    @DisplayName("ensureRealmRole: 创建 realm role, 第二次调用不抛异常")
    void ensureOrganizationMembership_shouldNotThrow() {
        port.ensureRealmRole(RealmRoleName.of("TENANT_ADMIN"));

        assertThatNoException().isThrownBy(() -> port.ensureRealmRole(RealmRoleName.of("TENANT_ADMIN"))
        );
    }



    // ensureUserRealmRole
    // 语义是：
    //  - 第一次：给用户分配角色
    //  - 第二次：角色已分配，仍然成功
    //  - 不重复、不报 conflict
    @Test
    @DisplayName("ensureUserRealmRole: 第一次：给用户分配角色, 第二次：角色已分配，仍然成功, 不重复、不报 conflict")
    void ensureUserRealmRole_shouldNotThrow() {
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");

        port.ensureRealmRole(realmRoleName);

        UserId userId = port.ensureUser(
                TenantId.of("tenant-role"),
                Email.of("123@123.com"),
                TemporaryCredentialPolicy.defaultPolicy()
        );

        // 第一次分配 realm role
        port.ensureUserRealmRole(TenantId.of("tenant-role"), userId, realmRoleName);

        assertThatNoException().isThrownBy(() -> port.ensureUserRealmRole(TenantId.of("tenant-role"), userId, realmRoleName));
    }

}

class FakeKeycloakAdminAdapterContractTest extends KeycloakAdminPortContractTest {

    @Override
    protected KeycloakAdminPort createPort() {
        return new FakeKeycloakAdminPort();
    }
}

// 命名为 IT（Integration Test）：由 Maven Failsafe 在 mvn verify 阶段运行，
// 不被 Surefire（mvn test）捡起，避免拖慢日常构建。
@Testcontainers
class RealKeycloakAdminAdapterContractIT extends KeycloakAdminPortContractTest {
    // static：所有测试方法共享一个 Keycloak 容器（启动一次约需 10-15s）
    // 非 static 则每个 @Test 都重启一次，太慢
    @Container
    static KeycloakContainer keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.5")
            .withRealmImportFile("keycloak/realm-export.json")
            ;


    @Override
    protected KeycloakAdminPort createPort() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakContainer.getAuthServerUrl())
                .realm("cdp-auth-pool")
                .clientId("cdp-provisioning-service")
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientSecret("local-cdp-cdp-provisioning-service-secret")
                .build();

        return new RealKeycloakAdminPortAdapter(keycloak, "cdp-auth-pool");
    }
}
