package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TemporaryCredentialPolicyTest {

    @Test
    void should_createTemporaryPasswordPolicy_when_firstLoginPasswordUpdateIsRequired() {
        TemporaryCredentialPolicy policy =
                TemporaryCredentialPolicy.temporaryPasswordWithRequiredPasswordUpdate();

        assertThat(policy.credentialType()).isEqualTo(TemporaryCredentialType.TEMPORARY_PASSWORD);
        assertThat(policy.requiredActions()).containsExactly(RequiredLoginAction.UPDATE_PASSWORD);
    }

    @Test
    void should_makeRequiredActionsImmutable_when_policyIsCreated() {
        TemporaryCredentialPolicy policy =
                new TemporaryCredentialPolicy(
                        TemporaryCredentialType.TEMPORARY_PASSWORD,
                        Set.of(RequiredLoginAction.UPDATE_PASSWORD)
                );

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> policy.requiredActions().add(RequiredLoginAction.UPDATE_PASSWORD));
    }

    @Test
    void should_rejectPolicy_when_temporaryPasswordDoesNotRequireFirstLoginPasswordUpdate() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> new TemporaryCredentialPolicy(
                        TemporaryCredentialType.TEMPORARY_PASSWORD,
                        Set.of()
                ))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TemporaryCredentialPolicy"))
                .withMessageContaining("UPDATE_PASSWORD");
    }

    @Test
    void should_rejectPolicy_when_credentialTypeIsMissing() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> new TemporaryCredentialPolicy(
                        null,
                        Set.of(RequiredLoginAction.UPDATE_PASSWORD)
                ))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TemporaryCredentialPolicy"))
                .withMessageContaining("credential type");
    }

    @Test
    void should_rejectPolicy_when_requiredActionsAreMissing() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> new TemporaryCredentialPolicy(
                        TemporaryCredentialType.TEMPORARY_PASSWORD,
                        null
                ))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TemporaryCredentialPolicy"))
                .withMessageContaining("required actions");
    }
}
