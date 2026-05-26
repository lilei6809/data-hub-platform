package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EmailTest {

    @Test
    void should_createEmail_when_valueIsValid() {
        Email email = Email.of("jane.admin@example.com");

        assertThat(email.value()).isEqualTo("jane.admin@example.com");
    }

    @Test
    void should_implementValueEquality_when_valuesAreEqual() {
        Email email = Email.of("jane.admin@example.com");

        assertThat(email)
                .isEqualTo(Email.of("jane.admin@example.com"))
                .hasSameHashCodeAs(Email.of("jane.admin@example.com"));
    }

    @Test
    void should_rejectEmail_when_valueIsBlank() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> Email.of(" "))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("Email"))
                .withMessageContaining("must not be blank");
    }

    @Test
    void should_rejectEmail_when_valueDoesNotContainValidAtSeparator() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> Email.of("jane.admin.example.com"))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("Email"))
                .withMessageContaining("invalid format");
    }

    @Test
    void should_rejectEmail_when_valueExceedsMaximumLength() {
        String tooLongEmail = "a".repeat(245) + "@example.com";

        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> Email.of(tooLongEmail))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("Email"))
                .withMessageContaining("254");
    }

    @Test
    void should_maskEmail_when_convertedToLogSafeString() {
        Email email = Email.of("jane.admin@example.com");

        assertThat(email.masked()).isEqualTo("j***n@example.com");
        assertThat(email.toString())
                .contains(email.masked())
                .doesNotContain("jane.admin");
    }
}
