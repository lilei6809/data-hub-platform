package io.datahub.platform.iamprovisioning.domain.exception;

public class EventPublishException extends Exception {

    public EventPublishException(String message) {
        super(message);
    }
    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
