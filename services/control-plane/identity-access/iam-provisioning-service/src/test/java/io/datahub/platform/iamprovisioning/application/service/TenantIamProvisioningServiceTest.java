package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureAdminUserStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationMembershipStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureTenantAdminRoleStep;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.FakeKeycloakAdminPort;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryTenantIamProvisioningStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIamProvisioningServiceTest {

    private TenantIamProvisioningService  service;
    private FakeKeycloakAdminPort port;
    private InMemoryTenantIamProvisioningStateRepository repository;
    private EnsureOrganizationStep ensureOrganizationStep;
    private EnsureAdminUserStep ensureAdminUserStep;
    private EnsureTenantAdminRoleStep ensureTenantAdminRoleStep;
    private EnsureOrganizationMembershipStep ensureOrganizationMembershipStep;


    @BeforeEach
    void setUp() {
        port = new FakeKeycloakAdminPort();
        repository = new InMemoryTenantIamProvisioningStateRepository();
        ensureOrganizationStep = new EnsureOrganizationStep(port);
        ensureAdminUserStep = new EnsureAdminUserStep(port);
        ensureTenantAdminRoleStep = new EnsureTenantAdminRoleStep(port);
        ensureOrganizationMembershipStep = new EnsureOrganizationMembershipStep(port);

        service = new TenantIamProvisioningService(repository, List.of(
                ensureOrganizationStep, ensureAdminUserStep, ensureTenantAdminRoleStep, ensureOrganizationMembershipStep
        ));
    }


    // 测试环境 1:  成功 provisioning
    @Test
    @DisplayName("TenantIamProvisioningService:  成功 provisioning, 应创建 organization with adminUser with realm roles")
    void iamProvisioningService_provisioningWithAdminUserWithRealmRoles() {
        TenantId tenantId = TenantId.of("tenant-abc");
        Email adminEmail = Email.of("admin@abc.com");
        TenantIamDesiredState desiredState = TenantIamDesiredState.ofMinimalInput(
                tenantId,
                TenantName.of("abc"),
                Tier.of("BASIC"),
                adminEmail
                );
        CorrelationId correlationId = CorrelationId.newCorrelationId();

        service.provisionTenantIam(desiredState, correlationId);

        ConcurrentHashMap<TenantId, FakeKeycloakAdminPort.StoredOrganization> organizations = port.organizationsSnapshot();
        ConcurrentHashMap<Email, FakeKeycloakAdminPort.StoredUser> users = port.usersSnapshot();
        ConcurrentHashMap<UserId, Set<RealmRoleName>> userRoles = port.userRoleAssignmentsSnapshot();
        ConcurrentHashMap<OrganizationId, Set<UserId>> memberships = port.membershipsSnapshot();

        TenantIamProvisioningState persistedState = repository.findByTenantId(tenantId)
                .orElseThrow();

        assertThat(persistedState.getOverallStatus()).isEqualTo(IamProvisioningStatus.COMPLETED);
        assertThat(persistedState.isKeycloakOrganizationCreated()).isTrue();
        assertThat(persistedState.isAdminUserCreated()).isTrue();
        assertThat(persistedState.isDefaultRolesAssigned()).isTrue();
        assertThat(persistedState.isAdminUserMembershipCreated()).isTrue();

        assertThat(organizations).hasSize(1);
        FakeKeycloakAdminPort.StoredOrganization organization = organizations.get(tenantId);
        assertThat(organization).isNotNull();
        assertThat(organization.organizationId()).isNotNull();

        assertThat(users).hasSize(1);
        FakeKeycloakAdminPort.StoredUser adminUser = users.get(adminEmail);
        assertThat(adminUser).isNotNull();
        assertThat(adminUser.userId()).isNotNull();

        assertThat(userRoles)
                .containsKey(adminUser.userId());
        assertThat(userRoles.get(adminUser.userId()))
                .containsExactly(RealmRoleName.of("TENANT_ADMIN"));

        assertThat(memberships)
                .containsKey(organization.organizationId());
        assertThat(memberships.get(organization.organizationId()))
                .containsExactly(adminUser.userId());

    }
}
