package io.datahub.platform.iamprovisioning.application.service;

import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningInputSnapshotRepository;
import io.datahub.platform.iamprovisioning.application.port.out.repository.TenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamProvisioningState;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.ProvisioningEventContext;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class TenantIamProvisioningRetryScheduler {

    private final static int BATCH_SIZE = 5;

    private final TenantIamProvisioningStateRepository repository;
    private final TenantIamProvisioningInputSnapshotRepository inputSnapshotRepository;
    private final TenantIamProvisioningService useCase;
    private final ProvisioningStateTransactor transactor;

    private final String instanceId = System.getenv().getOrDefault("HOSTNAME",
            "unknown-" + UUID.randomUUID().toString().substring(0, 8));

    public TenantIamProvisioningRetryScheduler(TenantIamProvisioningStateRepository repository,
                                               TenantIamProvisioningInputSnapshotRepository inputSnapshotRepository,
                                               TenantIamProvisioningService useCase, ProvisioningStateTransactor stateTransactor) {
        this.repository = repository;
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.useCase = useCase;
        this.transactor = stateTransactor;
    }

    @Scheduled(fixedRate = 60_000)
    public void scheduled() {
        Instant now = Instant.now();
        List<TenantIamProvisioningState> retries = repository.claimBatchReadyForRetry(BATCH_SIZE, instanceId, now);

        if (retries.isEmpty()) {
            return;
        }

        for (TenantIamProvisioningState state : retries) {
            Optional<TenantIamProvisioningInputSnapshot> optional = inputSnapshotRepository.findByTenantId(state.getTenantId());

            if (optional.isEmpty()) {
                //TODO:
                // 这条记录没有办法 retry, 应该标记 failed, 并发通知
                log.atError()
                        .addKeyValue("event", "tenant_iam_provisioning_retry_failed")
                        .addKeyValue("tenantId", state.getTenantId())
                        .addKeyValue("correlationId", state.getWorkflowCorrelationId())
                        .addKeyValue("step", "IamProvisioningRetry")
                        .addKeyValue("retryable", false)
                        .addKeyValue("status", state.getOverallStatus())
                        .addKeyValue("retryCount", state.getRetryCount())
                        .log("Tenant IAM provisioning retry failed due to TenantIamProvisioningInputSnapshot not found");
                state.markFailed(
                        Instant.now(),
                        IamProvisioningFailureCode.RETRY_CONTEXT_MISSING,
                        "缺失重试所需信息, 无法重建 TenantIamDesiredState",
                        ProvisioningEventContext.of(Tier.of("UNKNOWN"), Email.of("UNKNOWN"), state.getWorkflowCorrelationId())
                );

                transactor.saveIamProvisionStateAndAppendEvents(state);

                continue;
            }

            TenantIamProvisioningInputSnapshot snapshot = optional.get();
            useCase.provisionTenantIamForRetry(snapshot.desiredState(), snapshot.correlationId());
        }
    }
}
