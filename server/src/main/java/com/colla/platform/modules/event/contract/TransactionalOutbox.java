package com.colla.platform.modules.event.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only port that must join the caller's database transaction.
 */
public interface TransactionalOutbox {

    UUID append(EventEnvelope event);

    default UUID append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    ) {
        return append(new EventEnvelope(
            UUID.randomUUID(),
            workspaceId,
            eventType,
            1,
            aggregateType,
            aggregateId,
            actorId,
            idempotencyKey,
            null,
            null,
            Instant.now(),
            payload
        ));
    }

    record EventEnvelope(
        UUID eventId,
        UUID workspaceId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        String idempotencyKey,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        Map<String, Object> payload
    ) {
        public EventEnvelope {
            payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }
}
