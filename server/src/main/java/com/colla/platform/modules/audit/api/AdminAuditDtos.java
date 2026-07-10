package com.colla.platform.modules.audit.api;

import com.colla.platform.modules.audit.domain.AuditModels.AuditLogEntry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AdminAuditDtos {
    private AdminAuditDtos() {
    }

    static AdminAuditLogEntryView auditLog(AuditLogEntry entry) {
        return new AdminAuditLogEntryView(
            entry.id(),
            entry.workspaceId(),
            entry.actorId(),
            entry.actorName(),
            entry.action(),
            entry.targetType(),
            entry.targetId(),
            entry.ipAddress(),
            entry.userAgent(),
            entry.metadata(),
            entry.createdAt(),
            new AdminAuditActor(entry.actorId(), entry.actorName(), entry.ipAddress(), entry.userAgent()),
            new AdminAuditTarget(entry.targetType(), entry.targetId()),
            new AdminAuditContext(entry.action(), sourceUi(entry), apiSurface(entry), entry.metadata()),
            sourceUi(entry),
            apiSurface(entry),
            riskTag(entry.action()),
            quickFilters(entry)
        );
    }

    private static String riskTag(String action) {
        if (action == null) {
            return "low";
        }
        if (action.contains("permission") || action.contains("role") || action.contains("disabled") || action.contains("deleted")) {
            return "high";
        }
        if (action.contains("updated") || action.contains("moved") || action.contains("created")) {
            return "medium";
        }
        return "low";
    }

    private static List<String> quickFilters(AuditLogEntry entry) {
        return java.util.stream.Stream.of(
                entry.action(),
                entry.targetType(),
                entry.actorId() == null ? null : "actor:" + entry.actorId()
            )
            .filter(value -> value != null && !value.isBlank())
            .toList();
    }

    private static String sourceUi(AuditLogEntry entry) {
        Object sourceUi = entry.metadata() == null ? null : entry.metadata().get("sourceUi");
        return sourceUi == null ? "unknown" : sourceUi.toString();
    }

    private static String apiSurface(AuditLogEntry entry) {
        Object apiSurface = entry.metadata() == null ? null : entry.metadata().get("apiSurface");
        return apiSurface == null ? "unknown" : apiSurface.toString();
    }

    record AdminAuditLogEntryView(
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
        Instant createdAt,
        AdminAuditActor actor,
        AdminAuditTarget target,
        AdminAuditContext context,
        String sourceUi,
        String apiSurface,
        String riskTag,
        List<String> quickFilters
    ) {
    }

    record AdminAuditActor(UUID actorId, String actorName, String ipAddress, String userAgent) {
    }

    record AdminAuditTarget(String targetType, UUID targetId) {
    }

    record AdminAuditContext(String action, String sourceUi, String apiSurface, Map<String, Object> metadata) {
    }
}
