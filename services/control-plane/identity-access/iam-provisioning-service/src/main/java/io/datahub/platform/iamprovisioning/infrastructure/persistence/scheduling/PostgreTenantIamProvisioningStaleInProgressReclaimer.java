package io.datahub.platform.iamprovisioning.infrastructure.persistence.scheduling;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PostgreTenantIamProvisioningStaleInProgressReclaimer {

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    public void reclaimStaleInProgressStatus(){

    }
}
