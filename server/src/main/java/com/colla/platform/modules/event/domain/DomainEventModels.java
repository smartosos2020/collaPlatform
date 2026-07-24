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
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        long aggregateSequence,
        UUID actorId,
        String idempotencyKey,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        Map<String, Object> payload,
        int retryCount,
        Instant createdAt
    ) {
    }
}
