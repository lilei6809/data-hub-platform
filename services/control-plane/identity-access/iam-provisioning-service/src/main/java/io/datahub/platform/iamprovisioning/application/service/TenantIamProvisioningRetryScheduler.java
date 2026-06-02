package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TenantIamProvisioningRetryScheduler {

    private final static int BATCH_SIZE = 5;

    private final TenantIamProvisioningStateRepository repository;
    private final TenantIamProvisioningService useCase;

    public TenantIamProvisioningRetryScheduler(TenantIamProvisioningStateRepository repository, TenantIamProvisioningService useCase) {
        this.repository = repository;
        this.useCase = useCase;
    }

    @Scheduled(fixedRate = 60_000)
    public void scheduled() {
        List<TenantIamProvisioningState> retries = repository.findReadyForRetry(Instant.now(), BATCH_SIZE);

        if (retries.isEmpty()) {
            return;
        }

        for (TenantIamProvisioningState retry : retries) {
//            useCase.provisionTenantIam();
        }
    }
}
