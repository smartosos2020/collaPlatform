package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectSpaceExperienceRolloutService;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceRolloutModels.RolloutView;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/experience-rollout")
public class ProjectSpaceExperienceRolloutController {
    private final ProjectSpaceExperienceRolloutService service;

    public ProjectSpaceExperienceRolloutController(
        ProjectSpaceExperienceRolloutService service
    ) {
        this.service = service;
    }

    @GetMapping
    public RolloutView get(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        return service.get(requireCurrentUser(authentication), spaceId);
    }

    private CurrentUser requireCurrentUser(Authentication authentication) {
        if (
            authentication == null
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)
        ) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }
}
