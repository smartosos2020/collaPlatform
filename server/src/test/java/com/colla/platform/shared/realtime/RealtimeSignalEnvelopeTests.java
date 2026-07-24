package com.colla.platform.shared.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeSignalEnvelopeTests {
    @Test
    void freezesAndCopiesSafeVersionedEnvelope() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("conversationId", UUID.randomUUID().toString());

        RealtimeSignalEnvelope envelope = envelope(source);
        source.put("extra", "not copied");

        assertThat(envelope.envelopeVersion()).isEqualTo(1);
        assertThat(envelope.signalVersion()).isEqualTo(1);
        assertThat(envelope.payload()).containsOnlyKeys("conversationId");
        assertThatThrownBy(() -> envelope.payload().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnknownVersionsAndSensitivePayload() {
        assertThatThrownBy(() -> new RealtimeSignalEnvelope(
            2, "notification.changed", 1, UUID.randomUUID(), UUID.randomUUID(),
            Audience.user(UUID.randomUUID()), new ObjectReference("notification", UUID.randomUUID()),
            new Sequence(SequenceScope.OBJECT, "notification:1", 1), Instant.now(), UUID.randomUUID(),
            "/api/notifications", Map.of()
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("envelope version");

        assertThatThrownBy(() -> envelope(Map.of("title", "must not cross transport")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsafe");
    }

    @Test
    void enforcesAudienceAndCalibrationBoundaries() {
        assertThatThrownBy(() -> new RealtimeSignalEnvelope(
            1, "notification.changed", 1, UUID.randomUUID(), UUID.randomUUID(),
            Audience.workspace(), new ObjectReference("notification", UUID.randomUUID()),
            new Sequence(SequenceScope.OBJECT, "notification:1", 1), Instant.now(), UUID.randomUUID(),
            "https://example.test/api/notifications", Map.of()
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("local API");
    }

    private static RealtimeSignalEnvelope envelope(Map<String, Object> payload) {
        return new RealtimeSignalEnvelope(
            1,
            "notification.changed",
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Audience.user(UUID.randomUUID()),
            new ObjectReference("notification", UUID.randomUUID()),
            new Sequence(SequenceScope.OBJECT, "notification:1", 1),
            Instant.now(),
            UUID.randomUUID(),
            "/api/notifications",
            payload
        );
    }
}
