package com.colla.platform.modules.im.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImRealtimeSignalDomainEventHandler implements DomainEventHandler {
    private static final Set<String> SIGNAL_TYPES = Set.of(
        "message.created",
        "message.edited",
        "message.revoked",
        "message.pinned",
        "message.unpinned",
        "message.reaction.toggled",
        "conversation.updated",
        "conversation.read",
        "unread.changed"
    );
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "im.realtime",
        1,
        Set.of(new Subscription("im.realtime.changed", 1)),
        true
    );
    private final TransactionalOutbox outbox;

    public ImRealtimeSignalDomainEventHandler(@Lazy TransactionalOutbox outbox) {
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
        String signalType = requiredString(payload, "signalType");
        if (!SIGNAL_TYPES.contains(signalType)) {
            throw new DomainEventHandlingException.Permanent("Invalid IM realtime signalType");
        }
        String objectType = requiredString(payload, "objectType");
        if (!Set.of("message", "conversation").contains(objectType)) {
            throw new DomainEventHandlingException.Permanent("Invalid IM realtime objectType");
        }
        UUID objectId = requiredUuid(payload, "objectId");
        String calibrationPath = requiredString(payload, "calibrationPath");

        Map<String, Object> signalPayload = new LinkedHashMap<>();
        signalPayload.put("recipientId", recipientId.toString());
        signalPayload.put("signalType", signalType);
        signalPayload.put("objectType", objectType);
        signalPayload.put("objectId", objectId.toString());
        signalPayload.put("sourceVersion", event.aggregateSequence());
        signalPayload.put("sequenceScope", "AUDIENCE");
        signalPayload.put("sequenceKey", "im:" + recipientId);
        signalPayload.put("calibrationPath", calibrationPath);
        signalPayload.put("safePayload", safePayload(payload));
        outbox.append(
            event.workspaceId(),
            "realtime.signal.requested",
            "im_signal",
            objectId,
            event.actorId(),
            signalPayload,
            "realtime:im:" + event.eventId()
        );
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new DomainEventHandlingException.Permanent("Missing IM realtime " + key);
        }
        return value.toString();
    }

    private static UUID requiredUuid(Map<String, Object> payload, String key) {
        try {
            return UUID.fromString(requiredString(payload, key));
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid IM realtime " + key);
        }
    }

    private static Map<String, Object> safePayload(Map<String, Object> payload) {
        Object value = payload.get("safePayload");
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }
}
