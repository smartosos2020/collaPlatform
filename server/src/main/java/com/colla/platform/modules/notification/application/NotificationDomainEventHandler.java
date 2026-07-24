package com.colla.platform.modules.notification.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.modules.platform.contract.PlatformObjectTypes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDomainEventHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "notification.projection",
        1,
        Set.of(new Subscription("notification.created", 1)),
        true
    );

    private final NotificationRepository notificationRepository;
    private final TransactionalOutbox outbox;

    public NotificationDomainEventHandler(
        NotificationRepository notificationRepository,
        @Lazy TransactionalOutbox outbox
    ) {
        this.notificationRepository = notificationRepository;
        this.outbox = outbox;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    @Transactional
    public void handle(EventMessage event) {
        Map<String, Object> payload = event.payload();
        UUID recipientId = requiredUuid(payload, "recipientId");
        String notificationType = stringValue(payload, "notificationType", "system");
        if (!notificationRepository.isEnabled(event.workspaceId(), recipientId, notificationType)) {
            return;
        }
        UUID targetId = optionalUuid(payload, "targetId");
        notificationRepository.create(
            event.workspaceId(),
            recipientId,
            event.actorId(),
            notificationType,
            requiredString(payload, "title"),
            stringValue(payload, "body", ""),
            canonicalType(payload.get("targetType")),
            targetId,
            optionalString(payload, "webPath"),
            canonicalDedupeKey(stringValue(payload, "dedupeKey", event.eventId().toString()))
        ).ifPresent(notification -> outbox.append(
            event.workspaceId(),
            "realtime.signal.requested",
            "notification",
            notification.id(),
            event.actorId(),
            Map.of(
                "recipientId", recipientId.toString(),
                "signalType", "notification.changed",
                "objectType", "notification",
                "objectId", notification.id().toString(),
                "sourceVersion", event.aggregateSequence(),
                "calibrationPath", "/api/notifications"
            ),
            "realtime:notification:" + notification.id()
        ));
    }

    private static UUID requiredUuid(Map<String, Object> payload, String key) {
        try {
            return UUID.fromString(requiredString(payload, key));
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid notification " + key);
        }
    }

    private static UUID optionalUuid(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid notification " + key);
        }
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null || value.isBlank()) {
            throw new DomainEventHandlingException.Permanent("Missing notification " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static String stringValue(Map<String, Object> payload, String key, String fallback) {
        String value = optionalString(payload, key);
        return value == null ? fallback : value;
    }

    private static String canonicalType(Object value) {
        return value == null ? null : PlatformObjectTypes.canonicalize(value.toString());
    }

    private static String canonicalDedupeKey(String value) {
        if (value.length() <= 128) {
            return value;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
