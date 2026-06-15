package com.colla.platform.shared.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebSocketEventPayload(
    String type,
    UUID eventId,
    Instant serverTime,
    UUID workspaceId,
    String objectType,
    UUID objectId,
    Map<String, Object> payload
) {
    public static WebSocketEventPayload of(String type, Map<String, Object> payload) {
        return of(type, null, null, null, payload);
    }

    public static WebSocketEventPayload of(
        String type,
        UUID workspaceId,
        String objectType,
        UUID objectId,
        Map<String, Object> payload
    ) {
        return new WebSocketEventPayload(type, UUID.randomUUID(), Instant.now(), workspaceId, objectType, objectId, payload);
    }
}
