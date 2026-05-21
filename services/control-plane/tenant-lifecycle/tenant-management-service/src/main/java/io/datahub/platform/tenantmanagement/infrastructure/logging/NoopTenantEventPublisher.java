package io.datahub.platform.tenantmanagement.infrastructure.logging;

import io.datahub.platform.tenantmanagement.application.TenantEventPublisher;
import io.datahub.platform.tenantmanagement.domain.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopTenantEventPublisher implements TenantEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopTenantEventPublisher.class);

    @Override
    public void publishTenantCreated(Tenant tenant) {
        log.info("tenant.created event placeholder tenantId={} status={}", tenant.tenantId(), tenant.status());
    }

    @Override
    public void publishTenantStatusChanged(Tenant tenant) {
        log.info("tenant.status.changed event placeholder tenantId={} status={}", tenant.tenantId(), tenant.status());
    }
}
