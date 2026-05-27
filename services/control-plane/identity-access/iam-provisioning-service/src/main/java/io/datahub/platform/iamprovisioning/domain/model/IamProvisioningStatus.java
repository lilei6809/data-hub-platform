package io.datahub.platform.iamprovisioning.domain.model;

public enum IamProvisioningStatus {
    IAM_PENDING,
    IAM_IN_PROGRESS,
    IAM_COMPLETED,
    IAM_FAILED,
    IAM_AWAITING_RETRY
}
