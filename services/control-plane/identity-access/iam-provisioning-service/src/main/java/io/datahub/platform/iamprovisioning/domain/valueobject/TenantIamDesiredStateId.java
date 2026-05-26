package io.datahub.platform.iamprovisioning.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record TenantIamDesiredStateId(UUID value) {

    public TenantIamDesiredStateId {
        Objects.requireNonNull(value, "TenantIamDesiredStateId must not be null");
    }

    public static TenantIamDesiredStateId generate() {
        return new TenantIamDesiredStateId(UUID.randomUUID());
    }

    public static TenantIamDesiredStateId of(UUID value) {
        return new TenantIamDesiredStateId(value);
    }

    public static TenantIamDesiredStateId of(String value) {
        return new TenantIamDesiredStateId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}