package com.colla.platform.modules.event.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public event-consumer contract. Implementations live in the module that owns
 * the resulting side effect and must be safe to invoke more than once.
 */
public interface DomainEventHandler {

    Descriptor descriptor();

    void handle(EventMessage event);

    record Subscription(String eventType, int eventVersion) {
        public Subscription {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("Event type is required");
            }
            if (eventVersion < 1) {
                throw new IllegalArgumentException("Event version must be positive");
            }
        }
    }

    record Descriptor(
        String handlerKey,
        int handlerVersion,
        Set<Subscription> subscriptions,
        boolean orderedByAggregate
    ) {
        public Descriptor {
            if (handlerKey == null || !handlerKey.matches("[a-z][a-z0-9._-]{2,95}")) {
                throw new IllegalArgumentException("Handler key must be a stable lowercase identifier");
            }
            if (handlerVersion < 1) {
                throw new IllegalArgumentException("Handler version must be positive");
            }
            subscriptions = subscriptions == null ? Set.of() : Set.copyOf(subscriptions);
            if (subscriptions.isEmpty()) {
                throw new IllegalArgumentException("Handler subscriptions are required");
            }
        }

        public boolean supports(String eventType, int eventVersion) {
            return subscriptions.contains(new Subscription(eventType, eventVersion));
        }
    }

    record EventMessage(
        UUID eventId,
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
        Map<String, Object> payload
    ) {
        public EventMessage {
            payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }
}
