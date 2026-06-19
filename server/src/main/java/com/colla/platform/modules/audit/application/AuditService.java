package com.colla.platform.modules.audit.application;

import com.colla.platform.modules.audit.domain.AuditModels.AuditLogEntry;
import com.colla.platform.modules.audit.infrastructure.AuditRepository;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditRepository auditRepository;
    private final PermissionService permissionService;

    public AuditService(AuditRepository auditRepository, PermissionService permissionService) {
        this.auditRepository = auditRepository;
        this.permissionService = permissionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(CurrentUser actor, String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        auditRepository.append(actor.workspaceId(), actor.id(), action, targetType, targetId, null, null, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
        UUID workspaceId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
    ) {
        if (workspaceId == null) {
            return;
        }
        auditRepository.append(workspaceId, actorId, action, targetType, targetId, ipAddress, userAgent, metadata);
    }

    public List<AuditLogEntry> list(CurrentUser currentUser, String action, String targetType, UUID targetId, UUID actorId, int limit) {
        permissionService.requireManageUsers(currentUser);
        return auditRepository.list(currentUser.workspaceId(), action, targetType, targetId, actorId, limit);
    }
}
