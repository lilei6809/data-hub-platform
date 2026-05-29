package io.datahub.platform.iamprovisioning.infrastructure.keycloak;

import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakAuthenticationException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakInvalidRequestException;
import io.datahub.platform.iamprovisioning.application.port.out.keycloak.exception.KeycloakTransientException;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.OrganizationAttributes;
import io.datahub.platform.iamprovisioning.domain.valueobject.OrganizationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.RealmRoleName;
import io.datahub.platform.iamprovisioning.domain.valueobject.TemporaryCredentialPolicy;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;
import io.datahub.platform.iamprovisioning.domain.valueobject.UserId;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.OrganizationMemberResource;
import org.keycloak.admin.client.resource.OrganizationMembersResource;
import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.admin.client.resource.OrganizationsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RealKeycloakAdminPortTest {

    private static final String REALM = "cdp-auth-pool";

    private Keycloak keycloak;
    private RealmResource realmResource;
    private OrganizationsResource organizationsResource;
    private RolesResource rolesResource;
    private UsersResource usersResource;
    private RealKeycloakAdminPort port;

    @BeforeEach
    void setUp() {
        keycloak = mock(Keycloak.class);
        realmResource = mock(RealmResource.class);
        organizationsResource = mock(OrganizationsResource.class);
        rolesResource = mock(RolesResource.class);
        usersResource = mock(UsersResource.class);

        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.organizations()).thenReturn(organizationsResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(realmResource.users()).thenReturn(usersResource);

        port = new RealKeycloakAdminPort(keycloak, REALM);
    }

    @Test
    void ensureOrganization_whenCreateSucceeds_shouldReturnCreatedOrganizationId() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationAttributes attributes = organizationAttributes(tenantId);
        Response created = response(201);
        when(created.getHeaderString("Location"))
                .thenReturn("http://localhost/admin/realms/cdp-auth-pool/organizations/org-123");
        when(organizationsResource.create(any(OrganizationRepresentation.class))).thenReturn(created);

        OrganizationId organizationId = port.ensureOrganization(tenantId, attributes);

        assertThat(organizationId).isEqualTo(OrganizationId.of("org-123"));
        ArgumentCaptor<OrganizationRepresentation> captor = forClass(OrganizationRepresentation.class);
        verify(organizationsResource).create(captor.capture());
        assertThat(captor.getValue())
                .extracting(OrganizationRepresentation::getName, OrganizationRepresentation::getDescription)
                .containsExactly("tenant-a", "Tenant A");
        assertThat(captor.getValue().getAttributes())
                .containsEntry("tenant_id", List.of("tenant-a"))
                .containsEntry("tier", List.of("BASIC"));
    }

    @Test
    void ensureOrganization_whenCreateConflicts_shouldFindExistingAndReconcileAttributes() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationAttributes attributes = organizationAttributes(tenantId);
        Response conflict = response(409);
        OrganizationResource organizationResource = mock(OrganizationResource.class);
        OrganizationRepresentation existing = organization("org-existing", "tenant-a", "Old Name", "FREE");

        when(organizationsResource.create(any(OrganizationRepresentation.class))).thenReturn(conflict);
        when(organizationsResource.search(tenantId.value(), true, 0, 1)).thenReturn(List.of(existing));
        when(organizationsResource.get("org-existing")).thenReturn(organizationResource);

        OrganizationId organizationId = port.ensureOrganization(tenantId, attributes);

        assertThat(organizationId).isEqualTo(OrganizationId.of("org-existing"));
        assertThat(existing.getDescription()).isEqualTo("Tenant A");
        assertThat(existing.getAttributes()).containsEntry("tier", List.of("BASIC"));
        verify(organizationResource).update(existing);
    }

    @Test
    void ensureOrganization_whenCreateConflictsAndExistingAlreadyMatches_shouldNotUpdate() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationAttributes attributes = organizationAttributes(tenantId);
        Response conflict = response(409);
        OrganizationRepresentation existing = organization("org-existing", "tenant-a", "Tenant A", "BASIC");

        when(organizationsResource.create(any(OrganizationRepresentation.class))).thenReturn(conflict);
        when(organizationsResource.search(tenantId.value(), true, 0, 1)).thenReturn(List.of(existing));

        OrganizationId organizationId = port.ensureOrganization(tenantId, attributes);

        assertThat(organizationId).isEqualTo(OrganizationId.of("org-existing"));
        verify(organizationsResource, never()).get(anyString());
    }

    @Test
    void ensureOrganization_whenCreateUnauthorized_shouldThrowAuthenticationException() {
        TenantId tenantId = TenantId.of("tenant-a");
        Response unauthorized = response(401);
        when(organizationsResource.create(any(OrganizationRepresentation.class))).thenReturn(unauthorized);

        assertThatThrownBy(() -> port.ensureOrganization(tenantId, organizationAttributes(tenantId)))
                .isInstanceOf(KeycloakAuthenticationException.class)
                .satisfies(ex -> assertThat(((KeycloakAuthenticationException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_ORGANIZATION));
    }

    @Test
    void ensureOrganization_whenCreateReturnsServerError_shouldThrowTransientException() {
        TenantId tenantId = TenantId.of("tenant-a");
        Response serverError = response(503);
        when(organizationsResource.create(any(OrganizationRepresentation.class))).thenReturn(serverError);

        assertThatThrownBy(() -> port.ensureOrganization(tenantId, organizationAttributes(tenantId)))
                .isInstanceOf(KeycloakTransientException.class)
                .satisfies(ex -> assertThat(((KeycloakTransientException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_ORGANIZATION));
    }

    @Test
    void ensureUser_whenCreateSucceeds_shouldReturnCreatedUserId() {
        TenantId tenantId = TenantId.of("tenant-a");
        Email email = Email.of("admin@tenant-a.com");
        Response created = mock(Response.class);

        when(usersResource.searchByEmail(email.value(), true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(created);
        when(created.getStatus()).thenReturn(201);
        when(created.getHeaderString("Location"))
                .thenReturn("http://localhost/admin/realms/cdp-auth-pool/users/user-123");

        UserId userId = port.ensureUser(tenantId, email, TemporaryCredentialPolicy.defaultPolicy());

        assertThat(userId).isEqualTo(UserId.of("user-123"));
    }

    @Test
    void ensureUser_whenCreateConflicts_shouldFindExistingUserByEmail() {
        TenantId tenantId = TenantId.of("tenant-a");
        Email email = Email.of("admin@tenant-a.com");
        Response conflict = mock(Response.class);
        UserRepresentation existing = new UserRepresentation();
        existing.setId("user-existing");

        when(usersResource.searchByEmail(email.value(), true)).thenReturn(List.of(), List.of(existing));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(conflict);
        when(conflict.getStatus()).thenReturn(409);

        UserId userId = port.ensureUser(tenantId, email, TemporaryCredentialPolicy.defaultPolicy());

        assertThat(userId).isEqualTo(UserId.of("user-existing"));
        verify(conflict, never()).getHeaderString("Location");
    }

    @Test
    void ensureUser_whenKeycloakRejectsRequest_shouldThrowInvalidRequest() {
        TenantId tenantId = TenantId.of("tenant-a");
        Email email = Email.of("admin@tenant-a.com");
        Response badRequest = mock(Response.class);

        when(usersResource.searchByEmail(email.value(), true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(badRequest);
        when(badRequest.getStatus()).thenReturn(400);
        when(badRequest.readEntity(String.class)).thenReturn("invalid user");

        assertThatThrownBy(() -> port.ensureUser(tenantId, email, TemporaryCredentialPolicy.defaultPolicy()))
                .isInstanceOf(KeycloakInvalidRequestException.class)
                .satisfies(ex -> assertThat(((KeycloakInvalidRequestException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_USER));
    }

    @Test
    void ensureUser_whenExistingUserFound_shouldReturnExistingUserIdWithoutCreate() {
        TenantId tenantId = TenantId.of("tenant-a");
        Email email = Email.of("admin@tenant-a.com");
        UserRepresentation existing = user("user-existing");

        when(usersResource.searchByEmail(email.value(), true)).thenReturn(List.of(existing));

        UserId userId = port.ensureUser(tenantId, email, TemporaryCredentialPolicy.defaultPolicy());

        assertThat(userId).isEqualTo(UserId.of("user-existing"));
        verify(usersResource, never()).create(any(UserRepresentation.class));
    }

    @Test
    void ensureUser_whenCreateUnauthorized_shouldThrowAuthenticationException() {
        TenantId tenantId = TenantId.of("tenant-a");
        Email email = Email.of("admin@tenant-a.com");
        Response unauthorized = response(403);

        when(usersResource.searchByEmail(email.value(), true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(unauthorized);

        assertThatThrownBy(() -> port.ensureUser(tenantId, email, TemporaryCredentialPolicy.defaultPolicy()))
                .isInstanceOf(KeycloakAuthenticationException.class)
                .satisfies(ex -> assertThat(((KeycloakAuthenticationException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_USER));
    }

    @Test
    void ensureUser_whenCreateReturnsServerError_shouldThrowTransientException() {
        TenantId tenantId = TenantId.of("tenant-a");
        Email email = Email.of("admin@tenant-a.com");
        Response serverError = response(502);

        when(usersResource.searchByEmail(email.value(), true)).thenReturn(List.of());
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(serverError);

        assertThatThrownBy(() -> port.ensureUser(tenantId, email, TemporaryCredentialPolicy.defaultPolicy()))
                .isInstanceOf(KeycloakTransientException.class)
                .satisfies(ex -> assertThat(((KeycloakTransientException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_USER));
    }

    @Test
    void ensureOrganizationMembership_whenMembershipAlreadyExists_shouldNotCreateMembership() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationId organizationId = OrganizationId.of("org-123");
        UserId userId = UserId.of("user-123");
        OrganizationMembersResource membersResource = mockMembershipLookup(organizationId);
        OrganizationMemberResource memberResource = mock(OrganizationMemberResource.class);

        when(membersResource.member(userId.value())).thenReturn(memberResource);
        when(memberResource.toRepresentation()).thenReturn(null);

        port.ensureOrganizationMembership(tenantId, organizationId, userId);

        verify(membersResource, never()).addMember(anyString());
    }

    @Test
    void ensureOrganizationMembership_whenMembershipMissingAndCreateSucceeds_shouldAddMember() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationId organizationId = OrganizationId.of("org-123");
        UserId userId = UserId.of("user-123");
        OrganizationMembersResource membersResource = mockMembershipLookup(organizationId);
        OrganizationMemberResource memberResource = mockMissingMembership(membersResource, userId);
        Response created = response(201);

        when(membersResource.addMember(userId.value())).thenReturn(created);

        port.ensureOrganizationMembership(tenantId, organizationId, userId);

        verify(memberResource).toRepresentation();
        verify(membersResource).addMember(userId.value());
    }

    @Test
    void ensureOrganizationMembership_whenCreateConflicts_shouldTreatAsAlreadyCreated() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationId organizationId = OrganizationId.of("org-123");
        UserId userId = UserId.of("user-123");
        OrganizationMembersResource membersResource = mockMembershipLookup(organizationId);
        mockMissingMembership(membersResource, userId);
        Response conflict = response(409);

        when(membersResource.addMember(userId.value())).thenReturn(conflict);

        port.ensureOrganizationMembership(tenantId, organizationId, userId);

        verify(membersResource).addMember(userId.value());
    }

    @Test
    void ensureOrganizationMembership_whenCreateUnauthorized_shouldThrowAuthenticationException() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationId organizationId = OrganizationId.of("org-123");
        UserId userId = UserId.of("user-123");
        OrganizationMembersResource membersResource = mockMembershipLookup(organizationId);
        mockMissingMembership(membersResource, userId);
        Response forbidden = response(403);

        when(membersResource.addMember(userId.value())).thenReturn(forbidden);

        assertThatThrownBy(() -> port.ensureOrganizationMembership(tenantId, organizationId, userId))
                .isInstanceOf(KeycloakAuthenticationException.class)
                .satisfies(ex -> assertThat(((KeycloakAuthenticationException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP));
    }

    @Test
    void ensureOrganizationMembership_whenCreateRejected_shouldThrowInvalidRequestException() {
        TenantId tenantId = TenantId.of("tenant-a");
        OrganizationId organizationId = OrganizationId.of("org-123");
        UserId userId = UserId.of("user-123");
        OrganizationMembersResource membersResource = mockMembershipLookup(organizationId);
        mockMissingMembership(membersResource, userId);
        Response badRequest = response(400);

        when(badRequest.readEntity(String.class)).thenReturn("invalid membership");
        when(membersResource.addMember(userId.value())).thenReturn(badRequest);

        assertThatThrownBy(() -> port.ensureOrganizationMembership(tenantId, organizationId, userId))
                .isInstanceOf(KeycloakInvalidRequestException.class)
                .satisfies(ex -> assertThat(((KeycloakInvalidRequestException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_ORGANIZATION_MEMBERSHIP));
    }

    @Test
    void ensureRealmRole_whenRoleExists_shouldNotCreateRole() {
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");
        RoleResource roleResource = mock(RoleResource.class);
        RoleRepresentation existing = role(realmRoleName.roleName());

        when(rolesResource.get(realmRoleName.roleName())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(existing);

        port.ensureRealmRole(realmRoleName);

        verify(rolesResource, never()).create(any(RoleRepresentation.class));
    }

    @Test
    void ensureRealmRole_whenRoleMissing_shouldCreateRole() {
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");
        RoleResource roleResource = mock(RoleResource.class);

        when(rolesResource.get(realmRoleName.roleName())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenThrow(new NotFoundException());

        port.ensureRealmRole(realmRoleName);

        ArgumentCaptor<RoleRepresentation> captor = forClass(RoleRepresentation.class);
        verify(rolesResource).create(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("TENANT_ADMIN");
    }

    @Test
    void ensureRealmRole_whenKeycloakAuthFails_shouldThrowAuthenticationException() {
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");
        RoleResource roleResource = mock(RoleResource.class);

        when(rolesResource.get(realmRoleName.roleName())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenThrow(new NotAuthorizedException("Bearer"));

        assertThatThrownBy(() -> port.ensureRealmRole(realmRoleName))
                .isInstanceOf(KeycloakAuthenticationException.class)
                .satisfies(ex -> assertThat(((KeycloakAuthenticationException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_REALM_ROLE));
    }

    @Test
    void ensureUserRealmRole_whenUserAlreadyHasRole_shouldNotAttachRole() {
        TenantId tenantId = TenantId.of("tenant-a");
        UserId userId = UserId.of("user-123");
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");
        RoleScopeResource realmRoleScope = mockUserRoleScope(userId);

        when(realmRoleScope.listAll()).thenReturn(List.of(role("TENANT_ADMIN")));

        port.ensureUserRealmRole(tenantId, userId, realmRoleName);

        verify(realmRoleScope, never()).add(anyList());
        verifyNoInteractions(rolesResource);
    }

    @Test
    void ensureUserRealmRole_whenUserDoesNotHaveRole_shouldAttachRole() {
        TenantId tenantId = TenantId.of("tenant-a");
        UserId userId = UserId.of("user-123");
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");
        RoleScopeResource realmRoleScope = mockUserRoleScope(userId);
        RoleResource roleResource = mock(RoleResource.class);
        RoleRepresentation role = role("TENANT_ADMIN");

        when(realmRoleScope.listAll()).thenReturn(List.of());
        when(rolesResource.get(realmRoleName.roleName())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(role);

        port.ensureUserRealmRole(tenantId, userId, realmRoleName);

        verify(realmRoleScope).add(List.of(role));
    }

    @Test
    void ensureUserRealmRole_whenListRolesAuthFails_shouldThrowAuthenticationException() {
        TenantId tenantId = TenantId.of("tenant-a");
        UserId userId = UserId.of("user-123");
        RealmRoleName realmRoleName = RealmRoleName.of("TENANT_ADMIN");
        RoleScopeResource realmRoleScope = mockUserRoleScope(userId);

        when(realmRoleScope.listAll()).thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> port.ensureUserRealmRole(tenantId, userId, realmRoleName))
                .isInstanceOf(KeycloakAuthenticationException.class)
                .satisfies(ex -> assertThat(((KeycloakAuthenticationException) ex).getOperation())
                        .isEqualTo(KeycloakOperation.ENSURE_USER_REALM_ROLE));
    }

    private OrganizationAttributes organizationAttributes(TenantId tenantId) {
        return new OrganizationAttributes(tenantId, TenantName.of("Tenant A"), Tier.of("BASIC"));
    }

    private Response response(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    private OrganizationRepresentation organization(String id, String name, String description, String tier) {
        OrganizationRepresentation organization = new OrganizationRepresentation();
        organization.setId(id);
        organization.setName(name);
        organization.setDescription(description);
        organization.setAttributes(Map.of(
                "tenant_id", List.of(name),
                "tier", List.of(tier)
        ));
        return organization;
    }

    private UserRepresentation user(String id) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        return user;
    }

    private RoleRepresentation role(String name) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        return role;
    }

    private OrganizationMembersResource mockMembershipLookup(OrganizationId organizationId) {
        OrganizationResource organizationResource = mock(OrganizationResource.class);
        OrganizationMembersResource membersResource = mock(OrganizationMembersResource.class);
        when(organizationsResource.get(organizationId.value())).thenReturn(organizationResource);
        when(organizationResource.members()).thenReturn(membersResource);
        return membersResource;
    }

    private OrganizationMemberResource mockMissingMembership(OrganizationMembersResource membersResource, UserId userId) {
        OrganizationMemberResource memberResource = mock(OrganizationMemberResource.class);
        when(membersResource.member(userId.value())).thenReturn(memberResource);
        when(memberResource.toRepresentation()).thenThrow(new NotFoundException());
        return memberResource;
    }

    private RoleScopeResource mockUserRoleScope(UserId userId) {
        UserResource userResource = mock(UserResource.class);
        RoleMappingResource roleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource realmRoleScope = mock(RoleScopeResource.class);
        when(usersResource.get(userId.value())).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(realmRoleScope);
        return realmRoleScope;
    }
}
