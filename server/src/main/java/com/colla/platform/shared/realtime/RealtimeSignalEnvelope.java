package com.colla.platform.shared.realtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Transport-neutral realtime invalidation contract. It intentionally depends
 * on JDK types only so Worker and Gateway can share it without importing each
 * other's Redis, WebSocket, or persistence implementations.
 */
public record RealtimeSignalEnvelope(
    int envelopeVersion,
    String signalType,
    int signalVersion,
    UUID signalId,
    UUID workspaceId,
    Audience audience,
    ObjectReference object,
    Sequence sequence,
    Instant occurredAt,
    UUID correlationId,
    String calibrationPath,
    Map<String, Object> payload
) {
    public static final int CURRENT_ENVELOPE_VERSION = 1;
    public static final int CURRENT_SIGNAL_VERSION = 1;
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9._-]{1,95}");
    private static final Pattern PAYLOAD_KEY = Pattern.compile("[a-z][A-Za-z0-9._-]{0,63}");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "token", "password", "secret", "title", "body", "content", "acl", "members", "authorization"
    );
    private static final int MAX_PAYLOAD_ENTRIES = 32;
    private static final int MAX_PAYLOAD_DEPTH = 3;
    private static final int MAX_STRING_LENGTH = 1024;

    public RealtimeSignalEnvelope {
        if (envelopeVersion != CURRENT_ENVELOPE_VERSION) {
            throw new IllegalArgumentException("Unsupported realtime envelope version: " + envelopeVersion);
        }
        requireIdentifier(signalType, "signalType");
        if (signalVersion != CURRENT_SIGNAL_VERSION) {
            throw new IllegalArgumentException("Unsupported realtime signal version: " + signalVersion);
        }
        if (signalId == null || workspaceId == null || audience == null || object == null || sequence == null
            || occurredAt == null || correlationId == null) {
            throw new IllegalArgumentException("Realtime envelope identity fields are required");
        }
        if (calibrationPath == null || !calibrationPath.startsWith("/api/") || calibrationPath.contains("://")) {
            throw new IllegalArgumentException("Realtime calibration path must be a local API path");
        }
        payload = immutablePayload(payload == null ? Map.of() : payload, 0);
    }

    public record Audience(AudienceKind kind, UUID recipientId) {
        public Audience {
            if (kind == null) {
                throw new IllegalArgumentException("Realtime audience kind is required");
            }
            if (kind == AudienceKind.USER && recipientId == null) {
                throw new IllegalArgumentException("User audience requires a recipient");
            }
            if (kind == AudienceKind.WORKSPACE && recipientId != null) {
                throw new IllegalArgumentException("Workspace audience cannot have a recipient");
            }
        }

        public static Audience user(UUID recipientId) {
            return new Audience(AudienceKind.USER, recipientId);
        }

        public static Audience workspace() {
            return new Audience(AudienceKind.WORKSPACE, null);
        }
    }

    public enum AudienceKind {
        USER,
        WORKSPACE
    }

    public record ObjectReference(String type, UUID id) {
        public ObjectReference {
            requireIdentifier(type, "object.type");
            if (id == null) {
                throw new IllegalArgumentException("Realtime object id is required");
            }
        }
    }

    public record Sequence(SequenceScope scope, String key, long value) {
        public Sequence {
            if (scope == null) {
                throw new IllegalArgumentException("Realtime sequence scope is required");
            }
            if (key == null || key.isBlank() || key.length() > 192) {
                throw new IllegalArgumentException("Realtime sequence key is invalid");
            }
            if (value < 0) {
                throw new IllegalArgumentException("Realtime sequence value must be non-negative");
            }
        }
    }

    public enum SequenceScope {
        OBJECT,
        AUDIENCE
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid realtime " + field);
        }
    }

    private static Map<String, Object> immutablePayload(Map<String, Object> source, int depth) {
        if (depth > MAX_PAYLOAD_DEPTH || source.size() > MAX_PAYLOAD_ENTRIES) {
            throw new IllegalArgumentException("Realtime payload exceeds the safe structural limit");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || !PAYLOAD_KEY.matcher(key).matches()
                || SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unsafe realtime payload key");
            }
            copy.put(key, immutableValue(value, depth + 1));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value, int depth) {
        if (value == null || value instanceof Boolean || value instanceof Number
            || value instanceof UUID || value instanceof Instant) {
            return value;
        }
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("Realtime payload string is too long");
            }
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, item) -> typed.put(String.valueOf(key), item));
            return immutablePayload(typed, depth);
        }
        if (value instanceof List<?> list) {
            if (depth > MAX_PAYLOAD_DEPTH || list.size() > MAX_PAYLOAD_ENTRIES) {
                throw new IllegalArgumentException("Realtime payload list exceeds the safe structural limit");
            }
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(immutableValue(item, depth + 1)));
            return List.copyOf(copy);
        }
        throw new IllegalArgumentException("Unsupported realtime payload value");
    }
}
