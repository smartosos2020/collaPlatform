package com.colla.platform.modules.audit.infrastructure;

import com.colla.platform.modules.audit.domain.AuditModels.AuditLogEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuditRepository {
    void append(
        UUID workspaceId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
    );

    List<AuditLogEntry> list(UUID workspaceId, String action, String targetType, UUID targetId, UUID actorId, int limit);
}
