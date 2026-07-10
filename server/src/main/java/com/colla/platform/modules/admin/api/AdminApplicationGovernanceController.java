package com.colla.platform.modules.admin.api;

import com.colla.platform.modules.admin.application.AdminApplicationGovernanceService;
import com.colla.platform.modules.admin.application.AdminApplicationGovernanceService.AdminApplicationGovernanceView;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/application-governance")
public class AdminApplicationGovernanceController {
    private final AdminApplicationGovernanceService governanceService;
    private final PermissionService permissionService;

    public AdminApplicationGovernanceController(
        AdminApplicationGovernanceService governanceService,
        PermissionService permissionService
    ) {
        this.governanceService = governanceService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public AdminApplicationGovernanceView overview(Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        if (!permissionService.canAccessAdmin(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return governanceService.overview(currentUser);
    }
}
