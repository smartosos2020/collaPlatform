package com.colla.platform.modules.audit.contract;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit boundary. Callers provide redacted facts, hashes and bounded differences.
 */
public interface AuditAppender {

    void append(AuditEntry entry);

    record AuditEntry(
        UUID workspaceId,
        UUID actorId,
        String action,
        String objectType,
        UUID objectId,
        String requestId,
        String correlationId,
        Instant occurredAt,
        Map<String, Object> redactedContext,
        String beforeHash,
        String afterHash,
        Map<String, Object> boundedDiff
    ) {
        public AuditEntry {
            redactedContext = redactedContext == null ? Map.of() : Map.copyOf(redactedContext);
            boundedDiff = boundedDiff == null ? Map.of() : Map.copyOf(boundedDiff);
        }
    }
}
