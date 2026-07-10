package com.colla.platform.modules.permission.api;

import com.colla.platform.modules.permission.application.PermissionGovernanceService;
import com.colla.platform.modules.permission.api.AdminPermissionDtos.AdminPermissionInspectionView;
import com.colla.platform.modules.permission.api.AdminPermissionDtos.AdminPermissionRiskSummaryView;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/permission-governance")
public class PermissionGovernanceController {
    private final PermissionGovernanceService permissionGovernanceService;

    public PermissionGovernanceController(PermissionGovernanceService permissionGovernanceService) {
        this.permissionGovernanceService = permissionGovernanceService;
    }

    @GetMapping("/inspect")
    public AdminPermissionInspectionView inspect(
        @RequestParam UUID userId,
        @RequestParam String resourceType,
        @RequestParam UUID resourceId,
        @RequestParam(defaultValue = "view") String action,
        Authentication authentication
    ) {
        return AdminPermissionDtos.inspection(
            permissionGovernanceService.inspect(currentUser(authentication), userId, resourceType, resourceId, action)
        );
    }

    @GetMapping("/risks")
    public AdminPermissionRiskSummaryView risks(
        @RequestParam(required = false) UUID knowledgeBaseId,
        Authentication authentication
    ) {
        return AdminPermissionDtos.riskSummary(permissionGovernanceService.risks(currentUser(authentication), knowledgeBaseId));
    }

    @GetMapping(value = "/risks/export", produces = "text/csv")
    public String exportRisks(
        @RequestParam(required = false) UUID knowledgeBaseId,
        Authentication authentication
    ) {
        return permissionGovernanceService.exportRisksCsv(currentUser(authentication), knowledgeBaseId);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
