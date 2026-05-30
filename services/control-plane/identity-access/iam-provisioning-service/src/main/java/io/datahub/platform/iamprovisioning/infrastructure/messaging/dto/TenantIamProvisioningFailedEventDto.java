package io.datahub.platform.iamprovisioning.infrastructure.messaging.dto;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;

import java.time.Instant;

public record TenantIamProvisioningFailedEventDto(
        String tenantId,
        String tier,
        String failureCode,
        String retryable,
        String correlationId,
        String occurredAt
) {
}
