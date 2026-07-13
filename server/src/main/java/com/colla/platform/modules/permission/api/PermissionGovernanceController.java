package com.colla.platform.modules.permission.api;

import com.colla.platform.modules.permission.application.PermissionGovernanceService;
import com.colla.platform.modules.permission.api.AdminPermissionDtos.AdminPermissionInspectionView;
import com.colla.platform.modules.permission.api.AdminPermissionDtos.AdminPermissionRiskSummaryView;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import java.util.List;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionMatrixEntry;
import com.colla.platform.modules.permission.domain.PermissionGovernanceModels.PermissionRiskRemediation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/matrix")
    public List<PermissionMatrixEntry> matrix(Authentication authentication) {
        return permissionGovernanceService.permissionMatrix(currentUser(authentication));
    }

    @PostMapping("/risks/{riskId}/remediation")
    public PermissionRiskRemediation remediate(
        @PathVariable String riskId,
        @RequestParam(defaultValue = "false") boolean confirm,
        Authentication authentication
    ) {
        return permissionGovernanceService.remediate(currentUser(authentication), riskId, confirm);
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
