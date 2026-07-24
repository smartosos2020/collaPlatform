package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class ProjectAuthorizationService implements ProjectAuthorization {
    private final PermissionService permissionService;

    public ProjectAuthorizationService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public void requireCreateProjects(CurrentUser user) {
        permissionService.requireCreateProjects(user);
    }

    @Override
    public void requireManageProjects(CurrentUser user) {
        permissionService.requireManageProjects(user);
    }
}
