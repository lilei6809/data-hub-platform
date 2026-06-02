package io.datahub.platform.iamprovisioning.infrastructure.messaging.dto;

import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisioningFailedEvent;

import java.sql.Timestamp;

public class TenantIamProvisioningFailedEventDtoMapper {

    public static TenantIamProvisioningFailedEventDto toDto(TenantIamProvisioningFailedEvent event) {
        return  new TenantIamProvisioningFailedEventDto(
                event.tenantId().value(),
                event.tier().value(),
                event.failureCode().name(),
                String.valueOf(event.retryable()),
                event.correlationId().value(),
                Timestamp.from(event.occurredAt()).toString()
        );
    }
}
