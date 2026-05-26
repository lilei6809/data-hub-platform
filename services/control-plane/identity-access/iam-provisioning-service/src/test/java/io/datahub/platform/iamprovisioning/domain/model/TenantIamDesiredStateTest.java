package io.datahub.platform.iamprovisioning.domain.model;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TenantIamDesiredStateTest {

    @Test
    void should_createDesiredStateWithMvpDefaults_when_minimalInputIsValid() {
        TenantIamDesiredState desiredState = TenantIamDesiredState.ofMinimalInput(
                TenantId.of("tenant-a"),
                TenantName.of("tenant-a"),
                Tier.of("standard"),
                Email.of("admin@example.com")
        );

        assertThat(desiredState.tenantId()).isEqualTo(TenantId.of("tenant-a"));
        assertThat(desiredState.tier()).isEqualTo(Tier.of("standard"));
        assertThat(desiredState.adminUser().email()).isEqualTo((Email.of("admin@example.com")));
        assertThat(desiredState.identityMode()).isEqualTo(IdentityMode.LOCAL_ONLY);
        assertThat(desiredState.realmStrategy()).isEqualTo(RealmStrategy.SHARED_REALM);
        assertThat(desiredState.identityProviders()).isEmpty();
        assertThat(desiredState.policies()).isEmpty();
    }

    @Test
    void should_keepExtensionListsImmutable_when_originalListsAreChangedAfterConstruction() {
        List<IdentityProviderConfig> identityProviders = new ArrayList<>();
        List<PolicyConfig> policies = new ArrayList<>();

        TenantIamDesiredState desiredState = new TenantIamDesiredState(
                TenantId.of("tenant-a"), TenantName.of("tenant-a"),
                Tier.of("enterprise"),
                AdminUser.initialTenantAdmin(Email.of("admin@example.com")),
                IdentityMode.LOCAL_ONLY,
                RealmStrategy.SHARED_REALM,
                identityProviders,
                policies);

        identityProviders.add(new IdentityProviderConfig());
        policies.add(new PolicyConfig());

        assertThat(desiredState.identityProviders()).isEmpty();
        assertThat(desiredState.policies()).isEmpty();
    }

    @Test
    void should_acceptExtensionPlaceholders_when_futureConfigurationIsPresent() {
        IdentityProviderConfig identityProvider = new IdentityProviderConfig();
        PolicyConfig policy = new PolicyConfig();

        TenantIamDesiredState desiredState = new TenantIamDesiredState(
                TenantId.of("tenant-a"), TenantName.of("tenant-a"),
                Tier.of("enterprise"),
                AdminUser.initialTenantAdmin(Email.of("admin@example.com")),
                IdentityMode.BROKERED_IDP,
                RealmStrategy.DEDICATED_REALM,
                List.of(identityProvider),
                List.of(policy));

        assertThat(desiredState.identityProviders()).containsExactly(identityProvider);
        assertThat(desiredState.policies()).containsExactly(policy);
        assertThat(desiredState.identityMode()).isEqualTo(IdentityMode.BROKERED_IDP);
        assertThat(desiredState.realmStrategy()).isEqualTo(RealmStrategy.DEDICATED_REALM);
    }

    @Test
    void should_rejectDesiredState_when_requiredFieldIsMissing() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> new TenantIamDesiredState(
                        null, TenantName.of("tenant-a"),
                        Tier.of("standard"),
                        AdminUser.initialTenantAdmin(Email.of("admin@example.com")),
                        IdentityMode.LOCAL_ONLY,
                        RealmStrategy.SHARED_REALM,
                        List.of(),
                        List.of()))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TenantIamDesiredState"))
                .withMessageContaining("tenantId");
    }

    @Test
    void should_rejectDesiredState_when_extensionListIsMissing() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> new TenantIamDesiredState(
                        TenantId.of("tenant-a"), TenantName.of("tenant-a"),
                        Tier.of("standard"),
                        AdminUser.initialTenantAdmin(Email.of("admin@example.com")),
                        IdentityMode.LOCAL_ONLY,
                        RealmStrategy.SHARED_REALM,
                        null,
                        List.of()))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TenantIamDesiredState"))
                .withMessageContaining("identityProviders");
    }

    @Test
    void should_rejectDesiredState_when_brokeredIdentityModeHasNoProviderConfig() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> new TenantIamDesiredState(
                        TenantId.of("tenant-a"), TenantName.of("tenant-a"),
                        Tier.of("enterprise"),
                        AdminUser.initialTenantAdmin(Email.of("admin@example.com")),
                        IdentityMode.BROKERED_IDP,
                        RealmStrategy.SHARED_REALM,
                        List.of(),
                        List.of()))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TenantIamDesiredState"))
                .withMessageContaining("identityProviders");
    }

    @Test
    void should_notExposeRawAdminEmail_when_convertedToString() {
        TenantIamDesiredState desiredState = TenantIamDesiredState.ofMinimalInput(
                TenantId.of("tenant-a"),TenantName.of("tenant-a"),
                Tier.of("standard"),
                Email.of("admin@example.com")
        );

        assertThat(desiredState.toString())
                .doesNotContain(("admin@example.com"))
                .contains("a***n@example.com");
    }
}
