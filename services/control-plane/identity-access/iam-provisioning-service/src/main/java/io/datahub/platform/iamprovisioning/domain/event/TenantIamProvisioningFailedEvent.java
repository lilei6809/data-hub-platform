package io.datahub.platform.iamprovisioning.domain.event;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;

import java.time.Instant;

public record TenantIamProvisioningFailedEvent(
        TenantId tenantId,
        Tier tier,
        IamProvisioningFailureCode failureCode, // 枚举，不是自由文本
        boolean retryable,       // 下游消费者据此决定是否发告警还是等待重试, 或者说 Audit BC 需要记录这个属性
        CorrelationId correlationId,
        Instant occurredAt
) implements DomainEvent{

    public static TenantIamProvisioningFailedEvent of(
            TenantId tenantId, Tier tier, IamProvisioningFailureCode failureCode, boolean retryable, CorrelationId correlationId, Instant occurredAt
    ){
        return new TenantIamProvisioningFailedEvent(tenantId, tier, failureCode, retryable, correlationId, occurredAt);
    }

    @Override
    public Instant occurredAt(){
        return occurredAt;
    }
}
