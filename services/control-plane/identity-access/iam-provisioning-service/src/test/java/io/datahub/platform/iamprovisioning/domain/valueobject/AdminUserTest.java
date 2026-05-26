package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AdminUserTest {

    @Test
    void should_createAdminUser_when_emailAndCredentialPolicyAreValid() {
        AdminUser adminUser = AdminUser.of(
                Email.of(("jane.admin@example.com")),
                TemporaryCredentialPolicy.temporaryPasswordWithRequiredPasswordUpdate()
        );

        assertThat(adminUser.email()).isEqualTo(Email.of(("jane.admin@example.com")));
        assertThat(adminUser.temporaryCredentialPolicy().credentialType())
                .isEqualTo(TemporaryCredentialType.TEMPORARY_PASSWORD);
    }

    @Test
    void should_createInitialTenantAdmin_when_emailStringIsValid() {
        AdminUser adminUser = AdminUser.initialTenantAdmin(Email.of("jane.admin@example.com"));

        assertThat(adminUser.email()).isEqualTo(Email.of(("jane.admin@example.com")));
        assertThat(adminUser.temporaryCredentialPolicy().requiredActions())
                .containsExactly(RequiredLoginAction.UPDATE_PASSWORD);
    }

    @Test
    void should_implementValueEquality_when_valuesAreEqual() {
        AdminUser adminUser = AdminUser.initialTenantAdmin(Email.of("jane.admin@example.com"));

        assertThat(adminUser)
                .isEqualTo(AdminUser.initialTenantAdmin(Email.of("jane.admin@example.com")))
                .hasSameHashCodeAs(AdminUser.initialTenantAdmin(Email.of("jane.admin@example.com")));
    }

    @Test
    void should_rejectAdminUser_when_emailIsMissing() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> AdminUser.of(
                        null,
                        TemporaryCredentialPolicy.temporaryPasswordWithRequiredPasswordUpdate()
                ))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("AdminUser"))
                .withMessageContaining("email");
    }

    @Test
    void should_rejectAdminUser_when_temporaryCredentialPolicyIsMissing() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> AdminUser.of(Email.of(("jane.admin@example.com")), null))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("AdminUser"))
                .withMessageContaining("temporary credential policy");
    }

    @Test
    void should_notExposeRawEmail_when_convertedToLogSafeString() {
        AdminUser adminUser = AdminUser.initialTenantAdmin(Email.of("jane.admin@example.com"));

        assertThat(adminUser.toString())
                .contains("j***n@example.com")
                .doesNotContain(("jane.admin@example.com"));
    }
}
