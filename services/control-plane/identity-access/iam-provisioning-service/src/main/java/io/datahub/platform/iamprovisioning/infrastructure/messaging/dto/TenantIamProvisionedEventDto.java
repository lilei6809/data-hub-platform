package io.datahub.platform.iamprovisioning.infrastructure.messaging.dto;

import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;

import java.time.Instant;

public record TenantIamProvisionedEventDto(
        String tenantId,
        String tier,
        String adminEmail,
        String correlationId,
        String occurredAt
) {
}
