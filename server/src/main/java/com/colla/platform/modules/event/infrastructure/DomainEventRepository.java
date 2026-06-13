package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DomainEventRepository {
    UUID append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    );

    List<DomainEvent> claimPending(int limit);

    void markProcessed(UUID eventId, Instant processedAt);

    void markFailed(UUID eventId, int retryCount, Instant nextAttemptAt, String errorMessage);
}
