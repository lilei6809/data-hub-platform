package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.exception.IamProvisioningException;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureAdminUserStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationMembershipStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureOrganizationStep;
import io.datahub.platform.iamprovisioning.application.pipeline.step.EnsureTenantAdminRoleStep;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakAuthenticationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakTransientException;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.FakeKeycloakAdminPort;
import io.datahub.platform.iamprovisioning.infrastructure.keycloak.KeycloakOperation;
import io.datahub.platform.iamprovisioning.infrastructure.messaging.InMemoryEventPublisher;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryTenantIamProvisioningStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIamProvisioningServiceTest {

    private TenantIamProvisioningService  service;
    private FakeKeycloakAdminPort port;
    private InMemoryTenantIamProvisioningStateRepository repository;
    private EnsureOrganizationStep ensureOrganizationStep;
    private EnsureAdminUserStep ensureAdminUserStep;
    private EnsureTenantAdminRoleStep ensureTenantAdminRoleStep;
    private EnsureOrganizationMembershipStep ensureOrganizationMembershipStep;
    private InMemoryEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        port = new FakeKeycloakAdminPort();
        repository = new InMemoryTenantIamProvisioningStateRepository();
        ensureOrganizationStep = new EnsureOrganizationStep(port);
        ensureAdminUserStep = new EnsureAdminUserStep(port);
        ensureTenantAdminRoleStep = new EnsureTenantAdminRoleStep(port);
        ensureOrganizationMembershipStep = new EnsureOrganizationMembershipStep(port);
        eventPublisher = new InMemoryEventPublisher();

        service = new TenantIamProvisioningService(repository,
                List.of(
                ensureOrganizationStep, ensureAdminUserStep, ensureTenantAdminRoleStep, ensureOrganizationMembershipStep),
                eventPublisher);
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

        assertThat(persistedState.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);
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

    @Test
    @DisplayName("TenantIamProvisioningService: 重复 provisioning 应复用已存在的 Keycloak 对象")
    void provisionTenantIam_shouldBeIdempotent_whenSameDesiredStateIsExecutedTwice() {
        TenantId tenantId = TenantId.of("tenant-abc");
        Email adminEmail = Email.of("admin@abc.com");
        TenantIamDesiredState desiredState = TenantIamDesiredState.ofMinimalInput(
                tenantId,
                TenantName.of("abc"),
                Tier.of("BASIC"),
                adminEmail
        );

        service.provisionTenantIam(desiredState, CorrelationId.newCorrelationId());

        FakeKeycloakAdminPort.StoredOrganization firstOrganization = port.organizationsSnapshot().get(tenantId);
        FakeKeycloakAdminPort.StoredUser firstAdminUser = port.usersSnapshot().get(adminEmail);

        // 重复调用: 测试幂等性
        service.provisionTenantIam(desiredState, CorrelationId.newCorrelationId());

        ConcurrentHashMap<TenantId, FakeKeycloakAdminPort.StoredOrganization> organizations = port.organizationsSnapshot();
        ConcurrentHashMap<Email, FakeKeycloakAdminPort.StoredUser> users = port.usersSnapshot();
        ConcurrentHashMap<UserId, Set<RealmRoleName>> userRoles = port.userRoleAssignmentsSnapshot();
        ConcurrentHashMap<OrganizationId, Set<UserId>> memberships = port.membershipsSnapshot();
        TenantIamProvisioningState persistedState = repository.findByTenantId(tenantId)
                .orElseThrow();

        assertThat(persistedState.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);
        assertThat(organizations).hasSize(1);
        assertThat(users).hasSize(1);
        assertThat(organizations.get(tenantId).organizationId()).isEqualTo(firstOrganization.organizationId());
        assertThat(users.get(adminEmail).userId()).isEqualTo(firstAdminUser.userId());
        assertThat(userRoles.get(firstAdminUser.userId()))
                .containsExactly(RealmRoleName.of("TENANT_ADMIN"));
        assertThat(memberships.get(firstOrganization.organizationId()))
                .containsExactly(firstAdminUser.userId());
    }


    // 2. 测试环境:  EnsureMembership 可重试故障注入, 前面步骤的状态不受影响, 且当前状态为可重试. 且重试保持幂等
    @Test
    @DisplayName("TenantIamProvisioningService: EnsureMembership 可重试故障后应进入等待重试，并可幂等恢复")
    void provisionTenantIam_shouldAwaitRetryAndRecoverIdempotently_whenMembershipHasRetryableFailure() {
        TenantId tenantId = TenantId.of("tenant-abc");
        Email adminEmail = Email.of("admin@abc.com");
        TenantIamDesiredState desiredState = TenantIamDesiredState.ofMinimalInput(
                tenantId,
                TenantName.of("abc"),
                Tier.of("BASIC"),
                adminEmail
        );

        port.scheduleFailures(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, 1,
                new KeycloakTransientException(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, tenantId, null));


        assertThatThrownBy(() -> service.provisionTenantIam(desiredState, CorrelationId.newCorrelationId()))
                // 注意: step 会将 KeycloakOperationException 翻译为  IamProvisioningException
                // 所以这个地方需要断言是 IamProvisioningException 的 instance
                .isInstanceOf(IamProvisioningException.class)
                .satisfies(throwable -> {
                    IamProvisioningException exception = (IamProvisioningException) throwable;
                    assertThat(exception.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE);
                    assertThat(exception.retryable()).isTrue();
                });

        ConcurrentHashMap<TenantId, FakeKeycloakAdminPort.StoredOrganization> organizations = port.organizationsSnapshot();
        ConcurrentHashMap<Email, FakeKeycloakAdminPort.StoredUser> users = port.usersSnapshot();
        ConcurrentHashMap<UserId, Set<RealmRoleName>> userRoles = port.userRoleAssignmentsSnapshot();
        ConcurrentHashMap<OrganizationId, Set<UserId>> memberships = port.membershipsSnapshot();
        TenantIamProvisioningState persistedState = repository.findByTenantId(tenantId)
                .orElseThrow();

        FakeKeycloakAdminPort.StoredOrganization targetOrg = organizations.get(tenantId);
        FakeKeycloakAdminPort.StoredUser targetUser = users.get(adminEmail);

        assertThat(persistedState.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_AWAITING_RETRY);
        assertThat(persistedState.getProvisioningFailureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE);
        assertThat(persistedState.getRetryCount()).isEqualTo(1);
        assertThat(persistedState.getNextRetryAt()).isNotNull();
        assertThat(persistedState.isKeycloakOrganizationCreated()).isTrue();
        assertThat(persistedState.isAdminUserCreated()).isTrue();
        assertThat(persistedState.isDefaultRolesAssigned()).isTrue();
        assertThat(persistedState.isAdminUserMembershipCreated()).isFalse();
        assertThat(organizations).hasSize(1);
        assertThat(users).hasSize(1);
        assertThat(targetOrg).isNotNull();
        assertThat(targetUser).isNotNull();
        assertThat(userRoles.get(targetUser.userId()))
                .containsExactly(RealmRoleName.of("TENANT_ADMIN"));
        assertThat(memberships).isEmpty();

        service.provisionTenantIam(desiredState, CorrelationId.newCorrelationId());

        ConcurrentHashMap<TenantId, FakeKeycloakAdminPort.StoredOrganization> recoveredOrganizations = port.organizationsSnapshot();
        ConcurrentHashMap<Email, FakeKeycloakAdminPort.StoredUser> recoveredUsers = port.usersSnapshot();
        ConcurrentHashMap<OrganizationId, Set<UserId>> recoveredMemberships = port.membershipsSnapshot();
        TenantIamProvisioningState recoveredState = repository.findByTenantId(tenantId)
                .orElseThrow();

        assertThat(recoveredState.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_COMPLETED);
        assertThat(recoveredState.isKeycloakOrganizationCreated()).isTrue();
        assertThat(recoveredState.isAdminUserCreated()).isTrue();
        assertThat(recoveredState.isDefaultRolesAssigned()).isTrue();
        assertThat(recoveredState.isAdminUserMembershipCreated()).isTrue();
        assertThat(recoveredOrganizations).hasSize(1);
        assertThat(recoveredUsers).hasSize(1);
        assertThat(recoveredOrganizations.get(tenantId).organizationId()).isEqualTo(targetOrg.organizationId());
        assertThat(recoveredUsers.get(adminEmail).userId()).isEqualTo(targetUser.userId());
        assertThat(recoveredMemberships.get(targetOrg.organizationId()))
                .containsExactly(targetUser.userId());

    }



    // 3. 测试环境:  EnsureMembership 不可重试故障注入, 前面步骤的状态不受影响, 当前状态为 Failed.
    @Test
    @DisplayName("TenantIamProvisioningService: EnsureMembership 不可重试故障后应进入失败终态")
    void provisionTenantIam_shouldFail_whenMembershipHasNonRetryableFailure() {
        TenantId tenantId = TenantId.of("tenant-abc");
        Email adminEmail = Email.of("admin@abc.com");
        TenantIamDesiredState desiredState = TenantIamDesiredState.ofMinimalInput(
                tenantId,
                TenantName.of("abc"),
                Tier.of("BASIC"),
                adminEmail
        );

        port.scheduleFailures(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, 1,
                new KeycloakAuthenticationException(tenantId, KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP, "forbidden", null));

        assertThatThrownBy(() -> service.provisionTenantIam(desiredState, CorrelationId.newCorrelationId()))
                .isInstanceOf(IamProvisioningException.class)
                .satisfies(throwable -> {
                    IamProvisioningException exception = (IamProvisioningException) throwable;
                    assertThat(exception.failureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED);
                    assertThat(exception.retryable()).isFalse();
                });

        ConcurrentHashMap<TenantId, FakeKeycloakAdminPort.StoredOrganization> organizations = port.organizationsSnapshot();
        ConcurrentHashMap<Email, FakeKeycloakAdminPort.StoredUser> users = port.usersSnapshot();
        ConcurrentHashMap<UserId, Set<RealmRoleName>> userRoles = port.userRoleAssignmentsSnapshot();
        ConcurrentHashMap<OrganizationId, Set<UserId>> memberships = port.membershipsSnapshot();
        TenantIamProvisioningState persistedState = repository.findByTenantId(tenantId)
                .orElseThrow();

        FakeKeycloakAdminPort.StoredOrganization targetOrg = organizations.get(tenantId);
        FakeKeycloakAdminPort.StoredUser targetUser = users.get(adminEmail);

        assertThat(persistedState.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_FAILED);
        assertThat(persistedState.getProvisioningFailureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_AUTH_FAILED);
        assertThat(persistedState.getRetryCount()).isEqualTo(1);
        assertThat(persistedState.getNextRetryAt()).isNull();
        assertThat(persistedState.isKeycloakOrganizationCreated()).isTrue();
        assertThat(persistedState.isAdminUserCreated()).isTrue();
        assertThat(persistedState.isDefaultRolesAssigned()).isTrue();
        assertThat(persistedState.isAdminUserMembershipCreated()).isFalse();
        assertThat(organizations).hasSize(1);
        assertThat(users).hasSize(1);
        assertThat(targetOrg).isNotNull();
        assertThat(targetUser).isNotNull();
        assertThat(userRoles.get(targetUser.userId()))
                .containsExactly(RealmRoleName.of("TENANT_ADMIN"));
        assertThat(memberships).isEmpty();
    }


}
