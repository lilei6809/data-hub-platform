package io.datahub.platform.iamprovisioning.domain.exception;

// 事件发布失败
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }
    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
