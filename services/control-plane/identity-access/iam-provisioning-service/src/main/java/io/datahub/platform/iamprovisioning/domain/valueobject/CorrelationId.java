package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;

import java.io.Serializable;
import java.util.UUID;

public record CorrelationId(String value) implements Serializable {

    public CorrelationId {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("CorrelationId", "must not be blank");
        }
    }

    /** 无上游 Correlation ID 时，在入口处生成 */
    public static CorrelationId newCorrelationId() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    /** 从上游传播（HTTP Header、Kafka Header）重建 */
    public static CorrelationId of(String value) {
        return new CorrelationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
