package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;

import java.util.regex.Pattern;

public record TenantId(String value) {

    public static final int MAX_LENGTH = 128;

    private static final Pattern TENANT_SLUG_PATTERN =
            Pattern.compile("tenant-[a-z0-9]+(?:-[a-z0-9]+)*");

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("TenantId", "must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("TenantId", "must not exceed %d characters".formatted(MAX_LENGTH));
        }
        if (!TENANT_SLUG_PATTERN.matcher(value).matches()) {
//            throw new DomainValidationException(
//                    "TenantId",
//                    "must be a tenant slug such as tenant-acme-corp"
//            );
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
