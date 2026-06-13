package com.colla.platform.modules.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditModels {
    private AuditModels() {
    }

    public record AuditLogEntry(
        UUID id,
        UUID workspaceId,
        UUID actorId,
        String actorName,
        String action,
        String targetType,
        UUID targetId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata,
        Instant createdAt
    ) {
    }
}
