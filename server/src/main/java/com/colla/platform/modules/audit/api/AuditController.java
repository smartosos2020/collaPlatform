package com.colla.platform.modules.audit.api;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.audit.api.AdminAuditDtos.AdminAuditLogEntryView;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
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
    public List<AdminAuditLogEntryView> list(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) UUID targetId,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return auditService.list(currentUser(authentication), action, targetType, targetId, actorId, limit).stream()
            .map(AdminAuditDtos::auditLog)
            .toList();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @ResponseBody
    public String export(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) UUID targetId,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(defaultValue = "200") int limit,
        Authentication authentication
    ) {
        return auditService.export(currentUser(authentication), action, targetType, targetId, actorId, limit);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
