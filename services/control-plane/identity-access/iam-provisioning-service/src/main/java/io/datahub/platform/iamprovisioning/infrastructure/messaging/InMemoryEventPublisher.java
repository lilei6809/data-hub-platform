package io.datahub.platform.iamprovisioning.infrastructure.messaging;

import io.datahub.platform.iamprovisioning.application.port.out.EventPublisher;
import io.datahub.platform.iamprovisioning.domain.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventPublisher implements EventPublisher {

    private final Map<Class<?>, List<DomainEvent>> store = new ConcurrentHashMap<>();

    @Override
    public void publish(DomainEvent domainEvent) {
        store.computeIfAbsent(domainEvent.getClass(),
                k -> new ArrayList<>()
                ).add(domainEvent);
    }

    @SuppressWarnings("unchecked")
    // 测试辅助方法
    public <T> List<T> getPublishedEvents(Class<T> eventType) {
        return (List<T>) store.getOrDefault(eventType, List.of());
    }
}
