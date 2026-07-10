package com.colla.platform.modules.workspace.api;

import com.colla.platform.modules.workspace.application.WorkspaceDashboardService;
import com.colla.platform.modules.workspace.api.UserWorkspaceDtos.UserWorkspaceDashboardView;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceDashboardService dashboardService;

    public WorkspaceController(WorkspaceDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public UserWorkspaceDashboardView dashboard(Authentication authentication) {
        return UserWorkspaceDtos.dashboard(dashboardService.dashboard(currentUser(authentication)));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
