package io.datahub.platform.iamprovisioning.application.port.out;

import io.datahub.platform.iamprovisioning.domain.event.DomainEvent;

public interface EventPublisher {

    /**
     * 发布一个领域事件。
     * 语义：同步返回，发布失败抛出 EventPublishException。
     * 不承诺消息顺序、持久化或 exactly-once，这些由具体实现决定。
     */
    void publish(DomainEvent domainEvent);
}
