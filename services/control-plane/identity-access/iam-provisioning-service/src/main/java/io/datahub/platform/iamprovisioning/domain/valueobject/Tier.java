package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;

public record Tier(String value) {

    public static final int MAX_LENGTH = 64;

    public Tier {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("Tier", "must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("Tier", "must not exceed %d characters".formatted(MAX_LENGTH));
        }
    }

    public static Tier of(String value) {
        return new Tier(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
