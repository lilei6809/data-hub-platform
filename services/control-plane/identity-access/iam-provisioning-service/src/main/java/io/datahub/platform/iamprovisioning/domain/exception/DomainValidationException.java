package io.datahub.platform.iamprovisioning.domain.exception;

import java.util.Objects;

/**
 * Domain exception for invalid value object and aggregate invariants.
 */
public class DomainValidationException extends IllegalArgumentException {

    private final String typeName;
    private final String reason;

    public DomainValidationException(String typeName, String reason) {
        super("%s %s".formatted(typeName, reason));
        this.typeName = Objects.requireNonNull(typeName, "typeName must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public String typeName() {
        return typeName;
    }

    public String reason() {
        return reason;
    }
}
