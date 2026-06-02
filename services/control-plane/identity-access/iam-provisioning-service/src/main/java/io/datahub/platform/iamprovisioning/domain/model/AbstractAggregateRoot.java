package io.datahub.platform.iamprovisioning.domain.model;

import io.datahub.platform.iamprovisioning.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAggregateRoot {

    private final transient List<DomainEvent> events = new ArrayList<>();

    public void registerEvent(DomainEvent domainEvent) {
        events.add(domainEvent);
    }

    public List<DomainEvent> drainEvents() {
        List<DomainEvent> copy = List.copyOf(events);
        events.clear();
        return copy;
    }

    public List<DomainEvent> peekEvents() {
        return List.copyOf(events);
    }
}
