package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TierTest {

    @Test
    void should_createTier_when_valueIsNonBlank() {
        Tier tier = Tier.of("STANDARD");

        assertThat(tier.value()).isEqualTo("STANDARD");
        assertThat(tier.toString()).isEqualTo("STANDARD");
    }

    @Test
    void should_implementValueEquality_when_valuesAreEqual() {
        Tier tier = Tier.of("STANDARD");

        assertThat(tier)
                .isEqualTo(Tier.of("STANDARD"))
                .hasSameHashCodeAs(Tier.of("STANDARD"));
    }

    @Test
    void should_rejectTier_when_valueIsBlank() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> Tier.of(" "))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("Tier"))
                .withMessageContaining("must not be blank");
    }

    @Test
    void should_rejectTier_when_valueExceedsMaximumLength() {
        String tooLongTier = "A".repeat(65);

        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> Tier.of(tooLongTier))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("Tier"))
                .withMessageContaining("64");
    }
}
