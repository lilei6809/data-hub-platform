package io.datahub.platform.iamprovisioning.application.port.in;

import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;

public interface ProvisionTenantIamUseCase {

    // 方法签名就是这个 Use Case 的公开契约
    // 任何想触发 IAM onboarding 的入口，都只看这个接口
    void provisionTenantIam(TenantIamDesiredState desired, CorrelationId correlationId);


    void provisionTenantIamForRetry(TenantIamDesiredState desired, CorrelationId correlationId);
}
