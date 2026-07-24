package com.colla.platform.modules.im.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImRealtimeSignalDomainEventHandlerTests {

    @Test
    void mapsDurableRecipientFactToOrderedSafeSignal() {
        List<TransactionalOutbox.EventEnvelope> appended = new ArrayList<>();
        ImRealtimeSignalDomainEventHandler handler = new ImRealtimeSignalDomainEventHandler(event -> {
            appended.add(event);
            return event.eventId();
        });
        UUID recipientId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        handler.handle(event(14, Map.of(
            "recipientId", recipientId.toString(),
            "signalType", "message.created",
            "objectType", "message",
            "objectId", messageId.toString(),
            "calibrationPath", "/api/conversations/" + conversationId + "/messages?afterSeq=40",
            "safePayload", Map.of(
                "conversationId", conversationId.toString(),
                "messageId", messageId.toString(),
                "messageSeq", 41
            )
        )));

        assertThat(appended).hasSize(1);
        assertThat(appended.getFirst().eventType()).isEqualTo("realtime.signal.requested");
        assertThat(appended.getFirst().payload())
            .containsEntry("sourceVersion", 14L)
            .containsEntry("sequenceScope", "AUDIENCE")
            .containsEntry("sequenceKey", "im:" + recipientId)
            .containsEntry("calibrationPath", "/api/conversations/" + conversationId + "/messages?afterSeq=40")
            .doesNotContainKeys("content", "title", "body", "members");
        assertThat(appended.getFirst().payload().get("safePayload"))
            .isEqualTo(Map.of(
                "conversationId", conversationId.toString(),
                "messageId", messageId.toString(),
                "messageSeq", 41
            ));
    }

    @Test
    void rejectsUnknownSignalAndObjectTypes() {
        ImRealtimeSignalDomainEventHandler handler = new ImRealtimeSignalDomainEventHandler(event -> event.eventId());
        UUID recipientId = UUID.randomUUID();

        assertThatThrownBy(() -> handler.handle(event(1, Map.of(
            "recipientId", recipientId.toString(),
            "signalType", "message.private.payload",
            "objectType", "message",
            "objectId", UUID.randomUUID().toString(),
            "calibrationPath", "/api/conversations"
        )))).isInstanceOf(DomainEventHandlingException.Permanent.class);
        assertThatThrownBy(() -> handler.handle(event(2, Map.of(
            "recipientId", recipientId.toString(),
            "signalType", "conversation.updated",
            "objectType", "workspace",
            "objectId", UUID.randomUUID().toString(),
            "calibrationPath", "/api/conversations"
        )))).isInstanceOf(DomainEventHandlingException.Permanent.class);
    }

    private static EventMessage event(long sequence, Map<String, Object> payload) {
        return new EventMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "im.realtime.changed",
            1,
            "im_recipient",
            UUID.randomUUID(),
            sequence,
            UUID.randomUUID(),
            "im-realtime-test",
            UUID.randomUUID(),
            null,
            Instant.now(),
            payload
        );
    }
}
