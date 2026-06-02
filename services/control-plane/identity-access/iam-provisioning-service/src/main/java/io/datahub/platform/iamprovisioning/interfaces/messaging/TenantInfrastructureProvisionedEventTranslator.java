package io.datahub.platform.iamprovisioning.interfaces.messaging;

import io.datahub.platform.iamprovisioning.domain.event.TenantInfrastructureProvisionedEvent;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import io.datahub.platform.iamprovisioning.interfaces.messaging.dto.TenantInfrastructureProvisionedEventDto;

import java.time.Instant;

// 翻译过程中，Value Object 的构造器会自动执行不变量校验
// 比如 Email.of("not-an-email") 会抛出 DomainValidationException
// 这就是翻译层的"防腐"作用
public class TenantInfrastructureProvisionedEventTranslator {

    public TenantInfrastructureProvisionedEvent translate(TenantInfrastructureProvisionedEventDto dto){

        return TenantInfrastructureProvisionedEvent.of(
                TenantId.of(dto.tenantId()),
                TenantName.of(dto.tenantName()),
                Tier.of(dto.tier()),
                Email.of(dto.email()),
                dto.correlationId() != null ?
                        CorrelationId.of(dto.correlationId()) :
                        CorrelationId.newCorrelationId(),
                Instant.parse(dto.occurredAt())

        );
    }
}
