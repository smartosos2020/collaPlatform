package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RealtimeSignalDomainEventHandler implements DomainEventHandler {
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "realtime.signal",
        1,
        Set.of(new Subscription("realtime.signal.requested", 1)),
        true
    );
    private final RealtimeSignalRepository repository;
    private final TransactionalOutbox outbox;

    public RealtimeSignalDomainEventHandler(
        RealtimeSignalRepository repository,
        @Lazy TransactionalOutbox outbox
    ) {
        this.repository = repository;
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
        UUID recipientId = optionalUuid(payload, "recipientId");
        String signalType = requiredString(payload, "signalType");
        int signalVersion = optionalInt(payload, "signalVersion", RealtimeSignalEnvelope.CURRENT_SIGNAL_VERSION);
        String objectType = requiredString(payload, "objectType");
        UUID objectId = requiredUuid(payload, "objectId");
        long sequenceValue = requiredLong(payload, "sourceVersion");
        SequenceScope sequenceScope = optionalSequenceScope(payload);
        String sequenceKey = optionalString(payload, "sequenceKey");
        if (sequenceKey == null || sequenceKey.isBlank()) {
            sequenceKey = objectType + ":" + objectId;
        }
        String calibrationPath = requiredString(payload, "calibrationPath");
        UUID signalId = UUID.nameUUIDFromBytes(("realtime:" + event.eventId()).getBytes(StandardCharsets.UTF_8));
        RealtimeSignalEnvelope envelope;
        try {
            envelope = new RealtimeSignalEnvelope(
                RealtimeSignalEnvelope.CURRENT_ENVELOPE_VERSION,
                signalType,
                signalVersion,
                signalId,
                event.workspaceId(),
                recipientId == null ? Audience.workspace() : Audience.user(recipientId),
                new ObjectReference(objectType, objectId),
                new Sequence(sequenceScope, sequenceKey, sequenceValue),
                event.occurredAt() == null ? Instant.now() : event.occurredAt(),
                event.correlationId() == null ? event.eventId() : event.correlationId(),
                calibrationPath,
                safePayload(payload)
            );
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent(exception.getMessage());
        }

        repository.create(event.eventId(), envelope);
        outbox.append(new EventEnvelope(
            UUID.randomUUID(),
            event.workspaceId(),
            "realtime.transport.requested",
            1,
            "realtime_signal",
            signalId,
            event.actorId(),
            "realtime.transport:" + signalId,
            envelope.correlationId(),
            event.eventId(),
            Instant.now(),
            Map.of("signalId", signalId.toString())
        ));
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null || value.isBlank()) {
            throw new DomainEventHandlingException.Permanent("Missing realtime signal " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID optionalUuid(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid realtime signal " + key);
        }
    }

    private static UUID requiredUuid(Map<String, Object> payload, String key) {
        UUID value = optionalUuid(payload, key);
        if (value == null) {
            throw new DomainEventHandlingException.Permanent("Missing realtime signal " + key);
        }
        return value;
    }

    private static long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        try {
            long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid realtime signal " + key);
        }
    }

    private static int optionalInt(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
        } catch (RuntimeException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid realtime signal " + key);
        }
    }

    private static SequenceScope optionalSequenceScope(Map<String, Object> payload) {
        String value = optionalString(payload, "sequenceScope");
        if (value == null) {
            return SequenceScope.OBJECT;
        }
        try {
            return SequenceScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new DomainEventHandlingException.Permanent("Invalid realtime signal sequenceScope");
        }
    }

    @SuppressWarnings("unchecked")
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
