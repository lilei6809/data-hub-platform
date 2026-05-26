package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CorrelationIdTest {

    @Test
    void should_createCorrelationId_when_valueIsNonBlank() {
        CorrelationId correlationId = CorrelationId.of("workflow-123");

        assertThat(correlationId.value()).isEqualTo("workflow-123");
        assertThat(correlationId.toString()).isEqualTo("workflow-123");
    }

    @Test
    void should_generateCorrelationId_when_newCorrelationIdIsCalled() {
        CorrelationId correlationId = CorrelationId.newCorrelationId();

        assertThat(correlationId.value()).isNotBlank();
        assertThatValueIsUuid(correlationId.value());
    }

    @Test
    void should_implementValueEquality_when_valuesAreEqual() {
        CorrelationId correlationId = CorrelationId.of("workflow-123");

        assertThat(correlationId)
                .isEqualTo(CorrelationId.of("workflow-123"))
                .hasSameHashCodeAs(CorrelationId.of("workflow-123"));
    }

    @Test
    void should_rejectCorrelationId_when_valueIsBlank() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> CorrelationId.of(" "))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("CorrelationId"))
                .withMessageContaining("must not be blank");
    }

    private static void assertThatValueIsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
