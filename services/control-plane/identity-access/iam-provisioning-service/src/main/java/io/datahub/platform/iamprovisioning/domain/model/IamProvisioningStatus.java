package io.datahub.platform.iamprovisioning.domain.model;

public enum IamProvisioningStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    AWAITING_RETRY
}
