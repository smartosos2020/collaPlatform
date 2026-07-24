package com.colla.platform.shared.websocket;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebSocketEventPayload(
    int envelopeVersion,
    String type,
    int signalVersion,
    UUID eventId,
    Instant serverTime,
    UUID workspaceId,
    String audienceType,
    UUID recipientId,
    String objectType,
    UUID objectId,
    String sequenceScope,
    String sequenceKey,
    Long sequence,
    UUID correlationId,
    String calibrationPath,
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
        return new WebSocketEventPayload(
            0,
            type,
            0,
            UUID.randomUUID(),
            Instant.now(),
            workspaceId,
            null,
            null,
            objectType,
            objectId,
            null,
            null,
            null,
            null,
            null,
            payload
        );
    }

    public static WebSocketEventPayload fromRealtime(RealtimeSignalEnvelope envelope) {
        return new WebSocketEventPayload(
            envelope.envelopeVersion(),
            envelope.signalType(),
            envelope.signalVersion(),
            envelope.signalId(),
            envelope.occurredAt(),
            envelope.workspaceId(),
            envelope.audience().kind().name().toLowerCase(),
            envelope.audience().recipientId(),
            envelope.object().type(),
            envelope.object().id(),
            envelope.sequence().scope().name().toLowerCase(),
            envelope.sequence().key(),
            envelope.sequence().value(),
            envelope.correlationId(),
            envelope.calibrationPath(),
            envelope.payload()
        );
    }
}
