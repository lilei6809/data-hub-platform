package io.datahub.platform.iamprovisioning.domain.event;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();
}
