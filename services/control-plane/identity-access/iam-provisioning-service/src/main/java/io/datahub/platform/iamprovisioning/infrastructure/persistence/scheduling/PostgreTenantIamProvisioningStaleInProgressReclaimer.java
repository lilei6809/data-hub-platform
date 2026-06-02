package io.datahub.platform.iamprovisioning.infrastructure.persistence.scheduling;

import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class PostgreTenantIamProvisioningStaleInProgressReclaimer {

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final TenantIamProvisioningStateRepository repository;

    public PostgreTenantIamProvisioningStaleInProgressReclaimer(TenantIamProvisioningStateRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedRate = 60_000)
    public void reclaimStaleInProgressStatus(){
        int reclaimed = repository.reclaimStale(STALE_THRESHOLD);
        if (reclaimed > 0) {
            log.atWarn()
                    .addKeyValue("reclaimed_count", reclaimed)
                    .addKeyValue("stale_threshold_minutes", STALE_THRESHOLD.toMinutes())
                    .log("Reclaimed stale CLAIMING outbox events");
        }
    }
}
