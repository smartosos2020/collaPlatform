package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopePolicyTests {
    private final EventEnvelopePolicy policy = new EventEnvelopePolicy(new ObjectMapper());

    @Test
    void suppliesCorrelationAndOccurredAtDefaults() {
        UUID eventId = UUID.randomUUID();
        EventEnvelope normalized = policy.normalize(envelope(eventId, Map.of("title", "safe"), null, null));

        assertThat(normalized.correlationId()).isEqualTo(eventId);
        assertThat(normalized.occurredAt()).isNotNull();
        assertThat(normalized.payload()).containsEntry("title", "safe");
    }

    @Test
    void rejectsNestedSensitiveKeysAndOversizedPayloads() {
        assertThatThrownBy(() -> policy.normalize(envelope(
            UUID.randomUUID(),
            Map.of("nested", Map.of("refresh_token", "hidden")),
            UUID.randomUUID(),
            Instant.now()
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sensitive event payload key");

        assertThatThrownBy(() -> policy.normalize(envelope(
            UUID.randomUUID(),
            Map.of("content", "x".repeat(EventEnvelopePolicy.MAX_PAYLOAD_BYTES)),
            UUID.randomUUID(),
            Instant.now()
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payload exceeds");
    }

    @Test
    void rejectsInvalidIdentifiersAndVersions() {
        EventEnvelope source = envelope(UUID.randomUUID(), Map.of(), UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> policy.normalize(new EventEnvelope(
            source.eventId(),
            source.workspaceId(),
            "Invalid Event",
            0,
            source.aggregateType(),
            source.aggregateId(),
            source.actorId(),
            source.idempotencyKey(),
            source.correlationId(),
            source.causationId(),
            source.occurredAt(),
            source.payload()
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalizesPayloadKeysAndScansArrayValues() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zeta", 1);
        payload.put("alpha", Map.of("second", 2, "first", 1));
        EventEnvelope normalized = policy.normalize(envelope(
            UUID.randomUUID(),
            payload,
            UUID.randomUUID(),
            Instant.now()
        ));

        assertThat(new ArrayList<>(normalized.payload().keySet())).containsExactly("alpha", "zeta");
        assertThat(((Map<?, ?>) normalized.payload().get("alpha")).keySet().stream()
            .map(String::valueOf)
            .toList())
            .containsExactly("first", "second");
        assertThatThrownBy(() -> policy.normalize(envelope(
            UUID.randomUUID(),
            Map.of("items", new Object[] {Map.of("privateKey", "hidden")}),
            UUID.randomUUID(),
            Instant.now()
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sensitive event payload key");
    }

    private static EventEnvelope envelope(
        UUID eventId,
        Map<String, Object> payload,
        UUID correlationId,
        Instant occurredAt
    ) {
        return new EventEnvelope(
            eventId,
            UUID.randomUUID(),
            "project.updated",
            1,
            "project",
            UUID.randomUUID(),
            null,
            "project.updated:" + eventId,
            correlationId,
            null,
            occurredAt,
            payload
        );
    }
}
