package io.datahub.platform.iamprovisioning.infrastructure.messaging.dto;

import io.datahub.platform.iamprovisioning.domain.event.TenantIamProvisionedEvent;

import java.sql.Timestamp;

public class TenantIamProvisionedEventDtoMapper {

    public static TenantIamProvisionedEventDto toDto(TenantIamProvisionedEvent event){
        return  new TenantIamProvisionedEventDto(
                event.tenantId().value(),
                event.tier().value(),
                event.adminEmail().value(),
                event.correlationId().value(),
                Timestamp.from(event.occurredAt()).toString()
        );
    }
}
