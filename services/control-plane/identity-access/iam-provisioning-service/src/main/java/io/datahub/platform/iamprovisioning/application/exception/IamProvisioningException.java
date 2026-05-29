package io.datahub.platform.iamprovisioning.application.exception;

import io.datahub.platform.iamprovisioning.application.pipeline.IamProvisioningStep;
import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;

public class IamProvisioningException extends RuntimeException {
    private final IamProvisioningStep step;
    private final IamProvisioningFailureCode failureCode;
    private final boolean retryable;
    public IamProvisioningException(IamProvisioningStep step, IamProvisioningFailureCode failureCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public static IamProvisioningException missingContextValue(
            IamProvisioningStep step,
            String fieldName
    ){
        return new IamProvisioningException(
                step,
                IamProvisioningFailureCode.PIPELINE_CONTEXT_MISSING,
                "Step " + step.name() + " requires " + fieldName + ", but it is missing from StepExecutionContext",
                false,
                null

        );
    }



    public IamProvisioningFailureCode failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public IamProvisioningStep step() {
        return step;
    }

}
