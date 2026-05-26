package io.datahub.platform.iamprovisioning.domain.exception;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningStatus;

/**
 * 调用者试图执行一个不符合 IAM provisioning 状态机规则的状态转移
 */
public class InvalidIamProvisioningStateTransitionException extends IllegalStateException {
    public InvalidIamProvisioningStateTransitionException(
            IamProvisioningStatus currentStatus,
            IamProvisioningStatus targetStatus,
            String reason
    ) {
        super("Cannot transition IAM provisioning state from %s to %s,  reason: [%s]"
                .formatted(currentStatus, targetStatus, reason));
    }

}
