package io.datahub.platform.iamprovisioning.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DomainValidationExceptionTest {

    @Test
    void should_exposeTypeNameAndReason_when_created() {
        DomainValidationException exception = new DomainValidationException("Email", "invalid format");

        assertThat(exception)
                .hasMessage("Email invalid format");
        assertThat(exception.typeName()).isEqualTo("Email");
        assertThat(exception.reason()).isEqualTo("invalid format");
    }

    @Test
    void should_rejectNullTypeNameAndReason_when_created() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainValidationException(null, "invalid format"));

        assertThatNullPointerException()
                .isThrownBy(() -> new DomainValidationException("Email", null));
    }
}
