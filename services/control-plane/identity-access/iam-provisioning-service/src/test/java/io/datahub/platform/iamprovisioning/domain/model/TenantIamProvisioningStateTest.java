package io.datahub.platform.iamprovisioning.domain.model;

import io.datahub.platform.iamprovisioning.domain.exception.InvalidIamProvisioningStateTransitionException;
import io.datahub.platform.iamprovisioning.domain.valueobject.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TenantIamProvisioningStateTest {

    @Test
    void should_initializeProvisioningState_when_created() {
        Instant now = Instant.parse("2026-05-25T00:00:00Z");
        TenantId tenantId = TenantId.of("tenant-a");
        CorrelationId correlationId = CorrelationId.of("corr-1");

        TenantIamProvisioningState state = TenantIamProvisioningState.init(tenantId, correlationId, now);

        assertThat(state.getTenantId()).isEqualTo(tenantId);
        assertThat(state.getWorkflowCorrelationId()).isEqualTo(correlationId);
        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_PENDING);
        assertThat(state.getRetryCount()).isZero();
        assertThat(state.getCreatedAt()).isEqualTo(now);
        assertThat(state.getUpdatedAt()).isEqualTo(now);
        assertThat(state.getProvisionedAt()).isNull();
    }

    @Test
    void should_moveToAwaitingRetryAndRecordFailure_when_retryableAttemptFailsBeforeRetryLimit() {
        TenantIamProvisioningState state = newState();
        Instant startedAt = Instant.parse("2026-05-25T00:00:01Z");
        Instant failedAt = Instant.parse("2026-05-25T00:00:02Z");

        state.markInProgress(startedAt);
        state.markAwaitRetry(failedAt, IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE, "connection timeout",
                newEventContext());

        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_AWAITING_RETRY);
        assertThat(state.getRetryCount()).isEqualTo(1);
        assertThat(state.getLastAttemptAt()).isEqualTo(failedAt);
        assertThat(state.getUpdatedAt()).isEqualTo(failedAt);
        assertThat(state.getProvisioningFailureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE);
        assertThat(state.getFailureMessage()).isEqualTo("connection timeout");
    }

    @Test
    void should_moveToFailedAndKeepFailureDetails_when_retryableAttemptExhaustsRetryLimit() {
        TenantIamProvisioningState state = newState();

        for (int attempt = 1; attempt <= 5; attempt++) {
            state.markInProgress(Instant.parse("2026-05-25T00:00:0" + attempt + "Z"));
            state.markAwaitRetry(
                    Instant.parse("2026-05-25T00:01:0" + attempt + "Z"),
                    IamProvisioningFailureCode.KEYCLOAK_API_ERROR,
                    "api error " + attempt, newEventContext()
            );
        }

        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_FAILED);
        assertThat(state.getRetryCount()).isEqualTo(5);
        assertThat(state.getLastAttemptAt()).isEqualTo(Instant.parse("2026-05-25T00:01:05Z"));
        assertThat(state.getProvisioningFailureCode()).isEqualTo(IamProvisioningFailureCode.KEYCLOAK_API_ERROR);
        assertThat(state.getFailureMessage()).isEqualTo("api error 5");
    }

    @Test
    void should_keepTenantAndCorrelationIdStable_when_stateTransitions() {
        TenantId tenantId = TenantId.of("tenant-a");
        CorrelationId correlationId = CorrelationId.of("corr-1");
        TenantIamProvisioningState state = TenantIamProvisioningState.init(
                tenantId,
                correlationId,
                Instant.parse("2026-05-25T00:00:00Z")
        );

        state.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));
        state.markAwaitRetry(
                Instant.parse("2026-05-25T00:00:02Z"),
                IamProvisioningFailureCode.KEYCLOAK_UNAVAILABLE,
                "connection timeout", newEventContext()
        );
        state.markInProgress(Instant.parse("2026-05-25T00:00:03Z"));
        state.markFailed(
                Instant.parse("2026-05-25T00:00:04Z"),
                IamProvisioningFailureCode.UNKNOWN_ERROR,
                "terminal failure", newEventContext()
        );

        assertThat(state.getTenantId()).isEqualTo(tenantId);
        assertThat(state.getWorkflowCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void should_increaseRetryCountAndLastAttemptAtMonotonically_when_retrySequenceRuns() {
        TenantIamProvisioningState state = newState();
        Instant previousAttemptAt = null;

        for (int attempt = 1; attempt <= 5; attempt++) {
            Instant startedAt = Instant.parse("2026-05-25T00:00:0" + attempt + "Z");
            Instant failedAt = Instant.parse("2026-05-25T00:01:0" + attempt + "Z");

            state.markInProgress(startedAt);
            state.markAwaitRetry(failedAt,
                    IamProvisioningFailureCode.KEYCLOAK_API_ERROR,
                    "api error " + attempt,
                    newEventContext());

            assertThat(state.getRetryCount()).isEqualTo(attempt);
            if (previousAttemptAt != null) {
                assertThat(state.getLastAttemptAt()).isAfter(previousAttemptAt);
            }
            previousAttemptAt = state.getLastAttemptAt();
        }
    }

    private ProvisioningEventContext newEventContext() {
        return new ProvisioningEventContext(
                Tier.of("BASIC"),
                Email.of("abcnfd@abchyd.com"),
                CorrelationId.of("xxxxx")
        );
    }

    @Test
    void should_moveToFailedAndRecordFailure_when_nonRetryableAttemptFails() {
        TenantIamProvisioningState state = newState();
        Instant startedAt = Instant.parse("2026-05-25T00:00:01Z");
        Instant failedAt = Instant.parse("2026-05-25T00:00:02Z");

        state.markInProgress(startedAt);
        state.markFailed(failedAt, IamProvisioningFailureCode.ADMIN_USER_EXISTS, "admin user already exists", newEventContext());

        assertThat(state.getOverallStatus()).isEqualTo(IamProvisioningStatus.IAM_FAILED);
        assertThat(state.getRetryCount()).isEqualTo(0);
        assertThat(state.getLastAttemptAt()).isEqualTo(failedAt);
        assertThat(state.getUpdatedAt()).isEqualTo(failedAt);
        assertThat(state.getProvisioningFailureCode()).isEqualTo(IamProvisioningFailureCode.ADMIN_USER_EXISTS);
        assertThat(state.getFailureMessage()).isEqualTo("admin user already exists");
    }

    @Test
    void should_rejectFailedTransition_when_notInProgress() {
        TenantIamProvisioningState state = newState();

        assertThatExceptionOfType(InvalidIamProvisioningStateTransitionException.class)
                .isThrownBy(() -> state.markFailed(
                        Instant.parse("2026-05-25T00:00:01Z"),
                        IamProvisioningFailureCode.UNKNOWN_ERROR,
                        "unknown", newEventContext()
                ))
                .withMessageContaining("PENDING")
                .withMessageContaining("FAILED");
    }

    @Test
    void should_notExposeFailureMessageOrSecrets_when_convertedToString() {
        TenantIamProvisioningState state = newState();

        state.markInProgress(Instant.parse("2026-05-25T00:00:01Z"));
        state.markFailed(
                Instant.parse("2026-05-25T00:00:02Z"),
                IamProvisioningFailureCode.UNKNOWN_ERROR,
                "secret=temporary-password-123", newEventContext()
        );

        assertThat(state.toString())
                .contains("tenant-a", "FAILED", "UNKNOWN_ERROR")
                .doesNotContain("temporary-password-123")
                .doesNotContain("secret=");
    }

    private TenantIamProvisioningState newState() {
        return TenantIamProvisioningState.init(
                TenantId.of("tenant-a"),
                CorrelationId.of("corr-1"),
                Instant.parse("2026-05-25T00:00:00Z")
        );
    }
}
