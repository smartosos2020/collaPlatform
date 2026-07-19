package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectSpaceDtos.UserProjectSpaceView;
import com.colla.platform.modules.project.application.ProjectSpaceService;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/settings")
public class ProjectSpaceSettingsController {
    private final ProjectSpaceService projectSpaceService;

    public ProjectSpaceSettingsController(ProjectSpaceService projectSpaceService) {
        this.projectSpaceService = projectSpaceService;
    }

    @GetMapping
    public UserProjectSpaceView settings(@PathVariable UUID spaceId, Authentication authentication) {
        return ProjectSpaceDtos.user(projectSpaceService.getSettings(currentUser(authentication), spaceId));
    }

    @PatchMapping
    public UserProjectSpaceView update(
        @PathVariable UUID spaceId,
        @Valid @RequestBody UpdateProjectSpaceSettingsRequest request,
        Authentication authentication
    ) {
        return ProjectSpaceDtos.user(projectSpaceService.updateSettings(
            currentUser(authentication), spaceId, request.name(), request.description(), request.visibility()
        ));
    }

    @PostMapping("/disable")
    public UserProjectSpaceView disable(@PathVariable UUID spaceId, Authentication authentication) {
        return transition(spaceId, "disabled", authentication);
    }

    @PostMapping("/restore")
    public UserProjectSpaceView restore(@PathVariable UUID spaceId, Authentication authentication) {
        return transition(spaceId, "active", authentication);
    }

    @PostMapping("/archive")
    public UserProjectSpaceView archive(@PathVariable UUID spaceId, Authentication authentication) {
        return transition(spaceId, "archived", authentication);
    }

    private UserProjectSpaceView transition(UUID spaceId, String targetStatus, Authentication authentication) {
        return ProjectSpaceDtos.user(projectSpaceService.transitionSettings(currentUser(authentication), spaceId, targetStatus));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record UpdateProjectSpaceSettingsRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 2000) String description,
        String visibility
    ) {
    }
}
