package com.colla.platform.modules.notification.application;

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

class NotificationRealtimeSignalDomainEventHandlerTests {

    @Test
    void createdFactProducesObjectAndUnreadCalibrationSignals() {
        List<TransactionalOutbox.EventEnvelope> appended = new ArrayList<>();
        NotificationRealtimeSignalDomainEventHandler handler = new NotificationRealtimeSignalDomainEventHandler(event -> {
            appended.add(event);
            return event.eventId();
        });
        UUID recipientId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        handler.handle(event(9, Map.of(
            "recipientId", recipientId.toString(),
            "changeType", "created",
            "notificationId", notificationId.toString()
        )));

        assertThat(appended).hasSize(2);
        assertThat(appended).extracting(TransactionalOutbox.EventEnvelope::eventType)
            .containsOnly("realtime.signal.requested");
        assertThat(appended.get(0).payload())
            .containsEntry("signalType", "notification.created")
            .containsEntry("sourceVersion", 9L)
            .containsEntry("sequenceScope", "OBJECT")
            .containsEntry("calibrationPath", "/api/notifications")
            .doesNotContainKeys("title", "body", "content");
        assertThat(appended.get(1).payload())
            .containsEntry("signalType", "notification.unread.changed")
            .containsEntry("sequenceScope", "AUDIENCE")
            .containsEntry("sequenceKey", "notification-unread:" + recipientId)
            .containsEntry("calibrationPath", "/api/notifications/unread-count");
    }

    @Test
    void unreadOnlyFactProducesOneSignalAndInvalidFactsFailPermanently() {
        List<TransactionalOutbox.EventEnvelope> appended = new ArrayList<>();
        NotificationRealtimeSignalDomainEventHandler handler = new NotificationRealtimeSignalDomainEventHandler(event -> {
            appended.add(event);
            return event.eventId();
        });
        UUID recipientId = UUID.randomUUID();

        handler.handle(event(10, Map.of(
            "recipientId", recipientId.toString(),
            "changeType", "unread"
        )));

        assertThat(appended).hasSize(1);
        assertThat(appended.getFirst().payload()).containsEntry("signalType", "notification.unread.changed");
        assertThatThrownBy(() -> handler.handle(event(11, Map.of(
            "recipientId", recipientId.toString(),
            "changeType", "created"
        )))).isInstanceOf(DomainEventHandlingException.Permanent.class);
    }

    private static EventMessage event(long sequence, Map<String, Object> payload) {
        return new EventMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "notification.realtime.changed",
            1,
            "notification_recipient",
            UUID.randomUUID(),
            sequence,
            UUID.randomUUID(),
            "notification-realtime-test",
            UUID.randomUUID(),
            null,
            Instant.now(),
            payload
        );
    }
}
