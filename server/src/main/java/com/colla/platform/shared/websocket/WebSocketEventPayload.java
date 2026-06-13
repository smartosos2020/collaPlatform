package com.colla.platform.shared.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebSocketEventPayload(
    String type,
    UUID eventId,
    Instant serverTime,
    Map<String, Object> payload
) {
    public static WebSocketEventPayload of(String type, Map<String, Object> payload) {
        return new WebSocketEventPayload(type, UUID.randomUUID(), Instant.now(), payload);
    }
}
