package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.application.KnowledgeContentService;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentAcceptanceReport;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/knowledge-content/acceptance")
public class AdminKnowledgeAcceptanceController {
    private final KnowledgeContentService contentService;
    private final PermissionService permissionService;

    public AdminKnowledgeAcceptanceController(KnowledgeContentService contentService, PermissionService permissionService) {
        this.contentService = contentService;
        this.permissionService = permissionService;
    }

    @GetMapping("/v1")
    public KnowledgeContentAcceptanceReport report(Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        if (!permissionService.canAccessAdmin(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return contentService.acceptanceReport(currentUser);
    }
}

