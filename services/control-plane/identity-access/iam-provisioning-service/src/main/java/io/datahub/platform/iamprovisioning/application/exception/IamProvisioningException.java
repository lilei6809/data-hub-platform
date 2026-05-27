package io.datahub.platform.iamprovisioning.application.exception;

import io.datahub.platform.iamprovisioning.domain.model.IamProvisioningFailureCode;

public class IamProvisioningException extends RuntimeException {
    private final String stepName;
    private final IamProvisioningFailureCode failureCode;
    private final boolean retryable;
    public IamProvisioningException(String stepName, IamProvisioningFailureCode failureCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.stepName = stepName;
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public static IamProvisioningException missingContextValue(
            String stepName,
            String fieldName
    ){
        return new IamProvisioningException(
                stepName,
                IamProvisioningFailureCode.PIPELINE_CONTEXT_MISSING,
                "Step " + stepName + " requires " + fieldName + ", but it is missing from StepExecutionContext",
                false,
                null

        );
    }

    public String stepName() {
        return stepName;
    }

    public IamProvisioningFailureCode failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

}
