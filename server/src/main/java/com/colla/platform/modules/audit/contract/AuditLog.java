package com.colla.platform.modules.audit.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;

/**
 * Stable audit facade for business modules.
 */
public interface AuditLog {

    void log(CurrentUser actor, String action, String objectType, UUID objectId, Map<String, Object> metadata);

    void append(
        UUID workspaceId,
        UUID actorId,
        String action,
        String objectType,
        UUID objectId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
    );
}
