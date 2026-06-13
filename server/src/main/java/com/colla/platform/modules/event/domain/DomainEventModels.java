package com.colla.platform.modules.event.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class DomainEventModels {
    private DomainEventModels() {
    }

    public record DomainEvent(
        UUID id,
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        int retryCount,
        Instant createdAt
    ) {
    }
}
