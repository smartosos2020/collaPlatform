package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.ProjectSpaceDtos.LegacySpaceResolutionView;
import com.colla.platform.modules.project.api.ProjectSpaceDtos.UserProjectSpaceView;
import com.colla.platform.modules.project.application.ProjectSpaceService;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces")
public class ProjectSpaceController {
    private final ProjectSpaceService projectSpaceService;

    public ProjectSpaceController(ProjectSpaceService projectSpaceService) {
        this.projectSpaceService = projectSpaceService;
    }

    @GetMapping
    public List<UserProjectSpaceView> list(Authentication authentication) {
        return projectSpaceService.listVisible(currentUser(authentication)).stream()
            .map(ProjectSpaceDtos::user)
            .toList();
    }

    @PostMapping
    public UserProjectSpaceView create(
        @Valid @RequestBody CreateProjectSpaceRequest request,
        Authentication authentication
    ) {
        return ProjectSpaceDtos.user(projectSpaceService.create(
            currentUser(authentication),
            request.spaceKey(),
            request.name(),
            request.description(),
            request.visibility()
        ));
    }

    @GetMapping("/{spaceId}")
    public UserProjectSpaceView detail(@PathVariable UUID spaceId, Authentication authentication) {
        return ProjectSpaceDtos.user(projectSpaceService.getVisible(currentUser(authentication), spaceId));
    }

    @GetMapping("/legacy-resolve/{legacyProjectId}")
    public LegacySpaceResolutionView legacyResolve(@PathVariable UUID legacyProjectId, Authentication authentication) {
        return ProjectSpaceDtos.legacyResolution(
            projectSpaceService.resolveLegacySpace(currentUser(authentication), legacyProjectId)
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateProjectSpaceRequest(
        @Size(max = 64) String spaceKey,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 2000) String description,
        String visibility
    ) {
    }
}
