package com.colla.platform.modules.audit.application;

import com.colla.platform.modules.audit.domain.AuditModels.AuditLogEntry;
import com.colla.platform.modules.audit.infrastructure.AuditRepository;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.colla.platform.shared.request.RequestBoundaryContext.RequestBoundary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
        auditRepository.append(actor.workspaceId(), actor.id(), action, targetType, targetId, null, null, enrichMetadata(metadata));
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
        auditRepository.append(workspaceId, actorId, action, targetType, targetId, ipAddress, userAgent, enrichMetadata(metadata));
    }

    public List<AuditLogEntry> list(CurrentUser currentUser, String action, String targetType, UUID targetId, UUID actorId, int limit) {
        permissionService.requireManageUsers(currentUser);
        return auditRepository.list(currentUser.workspaceId(), action, targetType, targetId, actorId, limit);
    }

    public String export(CurrentUser currentUser, String action, String targetType, UUID targetId, UUID actorId, int limit) {
        List<AuditLogEntry> entries = list(currentUser, action, targetType, targetId, actorId, Math.min(limit, 200));
        String header = "id,created_at,actor,action,target_type,target_id";
        return header + "\n" + entries.stream()
            .map(entry -> String.join(",",
                csv(entry.id()),
                csv(entry.createdAt()),
                csv(entry.actorName()),
                csv(entry.action()),
                csv(entry.targetType()),
                csv(entry.targetId())
            ))
            .collect(Collectors.joining("\n"));
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private Map<String, Object> enrichMetadata(Map<String, Object> metadata) {
        RequestBoundary boundary = RequestBoundaryContext.current();
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        enriched.putIfAbsent("sourceUi", boundary.sourceUi());
        enriched.putIfAbsent("apiSurface", boundary.apiSurface());
        enriched.putIfAbsent("client", boundary.client());
        if (boundary.requestPath() != null && !boundary.requestPath().isBlank()) {
            enriched.putIfAbsent("requestPath", boundary.requestPath());
        }
        return enriched;
    }
}
