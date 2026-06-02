package io.datahub.platform.iamprovisioning.domain.event;

import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;

import java.time.Instant;

public record TenantIamProvisionedEvent(
        TenantId tenantId,       // 主业务标识，所有 BC 都认识
        Tier tier,
        Email adminEmail,   // 业务事实：谁是这个租户的初始管理员
        CorrelationId correlationId,
        Instant occurredAt
) implements DomainEvent{


    public static TenantIamProvisionedEvent of(
            TenantId tenantId, Tier tier, Email adminEmail, CorrelationId correlationId, Instant occurredAt
    ){
        return new TenantIamProvisionedEvent(tenantId, tier, adminEmail, correlationId, occurredAt);
    }

    @Override
    public Instant occurredAt(){
        return occurredAt;
    }
}
