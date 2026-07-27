package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectDetailService;
import com.colla.platform.modules.project.domain.ProjectDetailModels.DetailPreference;
import com.colla.platform.modules.project.domain.ProjectDetailModels.PreferenceCommand;
import com.colla.platform.modules.project.domain.ProjectDetailModels.ProjectDetail;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/project-detail")
public final class UserProjectDetailController {
    private final ProjectDetailService service;

    public UserProjectDetailController(ProjectDetailService service) {
        this.service = service;
    }

    @GetMapping
    public ProjectDetail get(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId);
    }

    @GetMapping("/preference")
    public DetailPreference preference(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.preference(currentUser(authentication), spaceId);
    }

    @PostMapping("/preference")
    public DetailPreference savePreference(
        @PathVariable UUID spaceId,
        @RequestBody PreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(
            currentUser(authentication), spaceId, command
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
