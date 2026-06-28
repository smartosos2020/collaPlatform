package com.colla.platform.modules.permission.api;

import com.colla.platform.modules.permission.application.PermissionGovernanceService;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionInspectionResult;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskSummary;
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
    public PermissionInspectionResult inspect(
        @RequestParam UUID userId,
        @RequestParam String resourceType,
        @RequestParam UUID resourceId,
        @RequestParam(defaultValue = "view") String action,
        Authentication authentication
    ) {
        return permissionGovernanceService.inspect(currentUser(authentication), userId, resourceType, resourceId, action);
    }

    @GetMapping("/risks")
    public PermissionRiskSummary risks(Authentication authentication) {
        return permissionGovernanceService.risks(currentUser(authentication));
    }

    @GetMapping(value = "/risks/export", produces = "text/csv")
    public String exportRisks(Authentication authentication) {
        return permissionGovernanceService.exportRisksCsv(currentUser(authentication));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
