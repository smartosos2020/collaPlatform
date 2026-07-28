package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ResourceWorklogService;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.MutateWorklogCommand;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.WorklogFoundation;
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
@RequestMapping("/api/project-spaces/{spaceId}/resource-planning/worklogs")
public final class UserResourceWorklogController {
    private final ResourceWorklogService service;

    public UserResourceWorklogController(ResourceWorklogService service) {
        this.service = service;
    }

    @GetMapping
    public WorklogFoundation get(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId);
    }

    @PostMapping
    public Worklog mutate(
        @PathVariable UUID spaceId,
        @RequestBody MutateWorklogCommand command,
        Authentication authentication
    ) {
        return service.mutate(currentUser(authentication), spaceId, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
