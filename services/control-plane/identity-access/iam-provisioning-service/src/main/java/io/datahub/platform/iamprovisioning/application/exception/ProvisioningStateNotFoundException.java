package io.datahub.platform.iamprovisioning.application.exception;

import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;

// 场景是 save() 时发现 affected rows = 0，但不是因为乐观锁冲突，而是因为这条记录根本不存在（比如被外部删除了）
// 实践中极少发生，但作为防御性编程应该覆盖
public class ProvisioningStateNotFoundException extends RuntimeException{

    public ProvisioningStateNotFoundException(TenantId tenantId) {
        super("TenantIamProvisioningState not found for tenantId=%s, "
                + "record may have been deleted externally"
                .formatted(tenantId));
    }
}
