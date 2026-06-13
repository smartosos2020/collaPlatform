package com.colla.platform.modules.audit.api;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.audit.domain.AuditModels.AuditLogEntry;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditLogEntry> list(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return auditService.list(currentUser(authentication), action, targetType, actorId, limit);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
