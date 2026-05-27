package io.datahub.platform.iamprovisioning.infrastructure.persistence;

import io.datahub.platform.iamprovisioning.application.port.out.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.application.port.out.TenantIamProvisioningStateRepositoryContractTest;

class InMemoryTenantIamProvisioningStateRepositoryTest extends TenantIamProvisioningStateRepositoryContractTest {

    @Override
    protected TenantIamProvisioningStateRepository createRepository() {
        return new InMemoryTenantIamProvisioningStateRepository();
    }
}
