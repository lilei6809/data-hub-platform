package io.datahub.platform.tenantmanagement.application;

import io.datahub.platform.tenantmanagement.domain.Tenant;

public interface TenantEventPublisher {

    void publishTenantCreated(Tenant tenant);

    void publishTenantStatusChanged(Tenant tenant);
}
