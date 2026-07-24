package com.colla.platform.modules.notification.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationRealtimeSignalDomainEventHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "notification.realtime",
        1,
        Set.of(new Subscription("notification.realtime.changed", 1)),
        true
    );
    private final TransactionalOutbox outbox;

    public NotificationRealtimeSignalDomainEventHandler(@Lazy TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    @Transactional
    public void handle(EventMessage event) {
        UUID recipientId = requiredUuid(event.payload(), "recipientId");
        String changeType = requiredString(event.payload(), "changeType");
        UUID notificationId = optionalUuid(event.payload(), "notificationId");
        if (!Set.of("created", "read", "unread").contains(changeType)) {
            throw new DomainEventHandlingException.Permanent("Invalid notification realtime changeType");
        }
        if (("created".equals(changeType) || "read".equals(changeType)) && notificationId == null) {
            throw new DomainEventHandlingException.Permanent("Missing notification realtime notificationId");
        }

        if (notificationId != null) {
            appendSignal(
                event,
                recipientId,
                "created".equals(changeType) ? "notification.created" : "notification.read",
                notificationId,
                "OBJECT",
                "notification:" + notificationId,
                "/api/notifications",
                "object"
            );
        }
        appendSignal(
            event,
            recipientId,
            "notification.unread.changed",
            recipientId,
            "AUDIENCE",
            "notification-unread:" + recipientId,
            "/api/notifications/unread-count",
            "unread"
        );
    }

    private void appendSignal(
        EventMessage event,
        UUID recipientId,
        String signalType,
        UUID objectId,
        String sequenceScope,
        String sequenceKey,
        String calibrationPath,
        String suffix
    ) {
        outbox.append(
            event.workspaceId(),
            "realtime.signal.requested",
            "notification_signal",
            objectId,
            event.actorId(),
            Map.of(
                "recipientId", recipientId.toString(),
                "signalType", signalType,
                "objectType", "notification",
                "objectId", objectId.toString(),
                "sourceVersion", event.aggregateSequence(),
                "sequenceScope", sequenceScope,
                "sequenceKey", sequenceKey,
                "calibrationPath", calibrationPath
            ),
            "realtime:notification:" + event.eventId() + ":" + suffix
        );
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new DomainEventHandlingException.Permanent("Missing notification realtime " + key);
        }
        return value.toString();
    }

    private static UUID requiredUuid(Map<String, Object> payload, String key) {
        UUID value = optionalUuid(payload, key);
        if (value == null) {
            throw new DomainEventHandlingException.Permanent("Missing notification realtime " + key);
        }
        return value;
    }

    private static UUID optionalUuid(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid notification realtime " + key);
        }
    }
}
