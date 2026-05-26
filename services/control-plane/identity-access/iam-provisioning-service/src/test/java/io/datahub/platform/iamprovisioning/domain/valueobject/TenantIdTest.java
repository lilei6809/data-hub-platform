package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TenantIdTest {

    @Test
    void should_createTenantId_when_valueIsValidTenantSlug() {
        TenantId tenantId = TenantId.of("tenant-acme-corp");

        assertThat(tenantId.value()).isEqualTo("tenant-acme-corp");
        assertThat(tenantId.toString()).isEqualTo("tenant-acme-corp");
    }

    @Test
    void should_implementValueEquality_when_valuesAreEqual() {
        TenantId tenantId = TenantId.of("tenant-acme-corp");

        assertThat(tenantId)
                .isEqualTo(TenantId.of("tenant-acme-corp"))
                .hasSameHashCodeAs(TenantId.of("tenant-acme-corp"));
    }

    @Test
    void should_rejectTenantId_when_valueIsBlank() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> TenantId.of(" "))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TenantId"))
                .withMessageContaining("must not be blank");
    }

    @Test
    void should_rejectTenantId_when_valueDoesNotUseTenantSlugFormat() {
        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> TenantId.of("Tenant Acme Corp"))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TenantId"))
                .withMessageContaining("tenant slug");
    }

    @Test
    void should_rejectTenantId_when_valueExceedsMaximumLength() {
        String tooLongTenantId = "tenant-" + "a".repeat(122);

        assertThatExceptionOfType(DomainValidationException.class)
                .isThrownBy(() -> TenantId.of(tooLongTenantId))
                .satisfies(exception -> assertThat(exception.typeName()).isEqualTo("TenantId"))
                .withMessageContaining("128");
    }
}
