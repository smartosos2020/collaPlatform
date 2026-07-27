package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemQueryService;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryPlan;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}")
public final class UserWorkItemQueryController {
    private final WorkItemQueryService service;

    public UserWorkItemQueryController(WorkItemQueryService service) {
        this.service = service;
    }

    @PostMapping("/work-item-queries:execute")
    public QueryResult execute(
        @PathVariable UUID spaceId,
        @Valid @RequestBody QueryDefinition query,
        Authentication authentication
    ) {
        return service.execute(currentUser(authentication), spaceId, query);
    }

    @PostMapping("/work-item-queries:explain")
    public QueryPlan explain(
        @PathVariable UUID spaceId,
        @Valid @RequestBody QueryDefinition query,
        Authentication authentication
    ) {
        return service.explain(currentUser(authentication), spaceId, query);
    }

    @PostMapping("/work-item-queries:dry-run")
    public QueryPlan dryRun(
        @PathVariable UUID spaceId,
        @Valid @RequestBody QueryDefinition query,
        Authentication authentication
    ) {
        return service.dryRun(currentUser(authentication), spaceId, query);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
