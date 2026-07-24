package com.colla.platform.shared.realtime;

import java.util.Map;
import java.util.UUID;

/**
 * Narrow public port for business modules that need to append a durable
 * realtime source event without depending on the event module implementation.
 */
public interface DurableRealtimeEventPublisher {
    void append(
        UUID workspaceId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        UUID actorId,
        Map<String, Object> payload,
        String idempotencyKey
    );
}
