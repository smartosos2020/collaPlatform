package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.infrastructure.RealtimeSignalRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    public RealtimeSignalDomainEventHandler(RealtimeSignalRepository repository) {
        this.repository = repository;
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
        String objectType = optionalString(payload, "objectType");
        UUID objectId = optionalUuid(payload, "objectId");
        long sourceVersion = requiredLong(payload, "sourceVersion");
        String calibrationPath = requiredString(payload, "calibrationPath");
        if (!calibrationPath.startsWith("/api/")) {
            throw new DomainEventHandlingException.Permanent("Realtime calibration path must be an API path");
        }
        repository.create(
            UUID.nameUUIDFromBytes(("realtime:" + event.eventId()).getBytes(StandardCharsets.UTF_8)),
            event.workspaceId(),
            event.eventId(),
            recipientId,
            signalType,
            objectType,
            objectId,
            sourceVersion,
            calibrationPath
        );
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
}
