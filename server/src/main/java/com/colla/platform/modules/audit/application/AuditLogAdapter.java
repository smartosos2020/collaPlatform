package com.colla.platform.modules.audit.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditLogAdapter implements AuditLog {
    private final AuditService auditService;

    public AuditLogAdapter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void log(CurrentUser actor, String action, String objectType, UUID objectId, Map<String, Object> metadata) {
        auditService.log(actor, action, objectType, objectId, metadata);
    }

    @Override
    public void append(
        UUID workspaceId,
        UUID actorId,
        String action,
        String objectType,
        UUID objectId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
    ) {
        auditService.log(workspaceId, actorId, action, objectType, objectId, ipAddress, userAgent, metadata);
    }
}
