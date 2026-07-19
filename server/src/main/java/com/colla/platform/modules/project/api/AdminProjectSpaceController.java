package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectSpaceDtos.AdminProjectSpaceView;
import com.colla.platform.modules.project.api.ProjectSpaceDtos.ProjectSpacePermissionExplanation;
import com.colla.platform.modules.project.application.ProjectSpaceService;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/project-spaces")
public class AdminProjectSpaceController {
    private final ProjectSpaceService projectSpaceService;

    public AdminProjectSpaceController(ProjectSpaceService projectSpaceService) {
        this.projectSpaceService = projectSpaceService;
    }

    @GetMapping
    public List<AdminProjectSpaceView> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String visibility,
        @RequestParam(defaultValue = "false") boolean includeArchived,
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(defaultValue = "0") int offset,
        Authentication authentication
    ) {
        CurrentUser currentUser = currentUser(authentication);
        return projectSpaceService.listGovernance(currentUser, status, visibility, includeArchived, limit, offset).stream()
            .map(space -> ProjectSpaceDtos.admin(space, projectSpaceService.hasContentAccess(space)))
            .toList();
    }

    @GetMapping("/{spaceId}")
    public AdminProjectSpaceView detail(@PathVariable UUID spaceId, Authentication authentication) {
        ProjectSpaceSummary space = projectSpaceService.getGovernance(currentUser(authentication), spaceId);
        return ProjectSpaceDtos.admin(space, projectSpaceService.hasContentAccess(space));
    }

    @GetMapping("/{spaceId}/permission-explanation")
    public ProjectSpacePermissionExplanation permissionExplanation(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        ProjectSpaceSummary space = projectSpaceService.getGovernance(currentUser(authentication), spaceId);
        boolean contentAccess = projectSpaceService.hasContentAccess(space);
        return new ProjectSpacePermissionExplanation(
            spaceId,
            "project.manage",
            true,
            contentAccess,
            contentAccess ? "project_space_members:" + space.currentUserRole() : "none",
            contentAccess
                ? "Enterprise governance and project-space membership are independently granted."
                : "Enterprise governance does not grant access to project-space collaboration content."
        );
    }

    @PostMapping("/{spaceId}/disable")
    public AdminProjectSpaceView disable(@PathVariable UUID spaceId, Authentication authentication) {
        return transition(spaceId, "disabled", authentication);
    }

    @PostMapping("/{spaceId}/restore")
    public AdminProjectSpaceView restore(@PathVariable UUID spaceId, Authentication authentication) {
        return transition(spaceId, "active", authentication);
    }

    @PostMapping("/{spaceId}/archive")
    public AdminProjectSpaceView archive(@PathVariable UUID spaceId, Authentication authentication) {
        return transition(spaceId, "archived", authentication);
    }

    private AdminProjectSpaceView transition(UUID spaceId, String status, Authentication authentication) {
        ProjectSpaceSummary space = projectSpaceService.transitionGovernance(currentUser(authentication), spaceId, status);
        return ProjectSpaceDtos.admin(space, projectSpaceService.hasContentAccess(space));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
