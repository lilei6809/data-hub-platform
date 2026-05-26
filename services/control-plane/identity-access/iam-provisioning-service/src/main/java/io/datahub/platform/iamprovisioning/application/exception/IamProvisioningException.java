package io.datahub.platform.iamprovisioning.application.exception;

public class IamProvisioningException extends RuntimeException {
    private final String stepName;
    private final String failureCode;
    private final boolean retryable;
    public IamProvisioningException(String stepName, String failureCode, String message, boolean retryable, Throwable cause) {
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
                "PIPELINE_CONTEXT_MISSING",
                "Step " + stepName + " requires " + fieldName + ", but it is missing from StepExecutionContext",
                false,
                null

        );
    }

    public String stepName() {
        return stepName;
    }

    public String failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

}
