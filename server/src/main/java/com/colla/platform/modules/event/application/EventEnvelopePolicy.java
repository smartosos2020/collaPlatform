package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EventEnvelopePolicy {
    static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    private static final Pattern TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9_.-]{2,127}");
    private static final Pattern AGGREGATE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
        "(?i)^(password|secret|token|access[_-]?key|refresh[_-]?token|private[_-]?key)$"
    );

    private final ObjectMapper objectMapper;

    public EventEnvelopePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope normalize(EventEnvelope source) {
        if (source == null) {
            throw new IllegalArgumentException("Event envelope is required");
        }
        require(source.eventId(), "Event id is required");
        require(source.workspaceId(), "Workspace id is required");
        require(source.aggregateId(), "Aggregate id is required");
        if (source.eventType() == null || !TYPE_PATTERN.matcher(source.eventType()).matches()) {
            throw new IllegalArgumentException("Event type must be a stable lowercase identifier");
        }
        if (source.eventVersion() < 1) {
            throw new IllegalArgumentException("Event version must be positive");
        }
        if (source.aggregateType() == null || !AGGREGATE_PATTERN.matcher(source.aggregateType()).matches()) {
            throw new IllegalArgumentException("Aggregate type must be a stable lowercase identifier");
        }
        if (source.idempotencyKey() != null && source.idempotencyKey().length() > 128) {
            throw new IllegalArgumentException("Event idempotency key exceeds 128 characters");
        }
        Map<String, Object> payload = source.payload() == null ? Map.of() : canonicalMap(source.payload());
        rejectSensitiveKeys(payload, "payload");
        byte[] serialized;
        try {
            serialized = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid event payload", exception);
        }
        if (serialized.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Event payload exceeds 262144 bytes");
        }
        UUID correlationId = source.correlationId() == null ? source.eventId() : source.correlationId();
        Instant occurredAt = source.occurredAt() == null ? Instant.now() : source.occurredAt();
        return new EventEnvelope(
            source.eventId(),
            source.workspaceId(),
            source.eventType(),
            source.eventVersion(),
            source.aggregateType(),
            source.aggregateId(),
            source.actorId(),
            source.idempotencyKey(),
            correlationId,
            source.causationId(),
            occurredAt,
            payload
        );
    }

    private Map<String, Object> canonicalMap(Map<?, ?> source) {
        Map<String, Object> sorted = new TreeMap<>();
        source.forEach((key, value) -> sorted.put(String.valueOf(key), canonicalValue(value)));
        return new LinkedHashMap<>(sorted);
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return canonicalMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalValue).toList();
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            java.util.ArrayList<Object> items = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index += 1) {
                items.add(canonicalValue(java.lang.reflect.Array.get(value, index)));
            }
            return List.copyOf(items);
        }
        return value;
    }

    private void rejectSensitiveKeys(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (SENSITIVE_KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("Sensitive event payload key is forbidden: " + path + "." + key);
                }
                rejectSensitiveKeys(entry.getValue(), path + "." + key);
            }
        } else if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                rejectSensitiveKeys(item, path + "[" + index + "]");
                index += 1;
            }
        } else if (value != null && value.getClass().isArray()) {
            for (int index = 0; index < java.lang.reflect.Array.getLength(value); index += 1) {
                rejectSensitiveKeys(java.lang.reflect.Array.get(value, index), path + "[" + index + "]");
            }
        }
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
