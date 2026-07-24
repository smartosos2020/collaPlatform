package com.colla.platform.shared.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebSocketEventPayloadTests {
    @Test
    void preservesOccurredAtSeparatelyFromGatewayServerTime() throws Exception {
        Instant occurredAt = Instant.parse("2020-07-24T08:00:00.123456789Z");
        RealtimeSignalEnvelope envelope = new RealtimeSignalEnvelope(
            1,
            "notification.created",
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Audience.user(UUID.randomUUID()),
            new ObjectReference("notification", UUID.randomUUID()),
            new Sequence(SequenceScope.OBJECT, "notification:test", 7),
            occurredAt,
            UUID.randomUUID(),
            "/api/notifications",
            Map.of("notificationId", UUID.randomUUID().toString())
        );

        WebSocketEventPayload payload = WebSocketEventPayload.fromRealtime(envelope);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(payload));

        assertThat(payload.occurredAt()).isEqualTo(occurredAt);
        assertThat(payload.serverTime()).isAfterOrEqualTo(occurredAt);
        assertThat(json.path("occurredAt").asText()).isEqualTo("2020-07-24T08:00:00.123456789Z");
        assertThat(json.path("serverTime").asText()).isNotBlank();
    }
}
