package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ProjectRegisterService;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.MutateCommand;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterEntry;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/project-register")
public final class UserProjectRegisterController {
    private final ProjectRegisterService service;

    public UserProjectRegisterController(ProjectRegisterService service) {
        this.service = service;
    }

    @GetMapping
    public List<RegisterSummary> list(
        @PathVariable UUID spaceId,
        @RequestParam(required = false) String type,
        Authentication authentication
    ) {
        return service.list(currentUser(authentication), spaceId, type);
    }

    @GetMapping("/{entryId}")
    public RegisterEntry get(
        @PathVariable UUID spaceId,
        @PathVariable UUID entryId,
        Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId, entryId);
    }

    @PostMapping
    public RegisterEntry create(
        @PathVariable UUID spaceId,
        @RequestBody CreateCommand command,
        Authentication authentication
    ) {
        return service.create(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/{entryId}:mutate")
    public RegisterEntry mutate(
        @PathVariable UUID spaceId,
        @PathVariable UUID entryId,
        @RequestBody MutateCommand command,
        Authentication authentication
    ) {
        return service.mutate(currentUser(authentication), spaceId, entryId, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
