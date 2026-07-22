package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemTypeApiDtos.UserWorkItemTypeSummary;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/work-item-types")
public class UserWorkItemTypeController {
    private final WorkItemTypeConfigurationService service;

    public UserWorkItemTypeController(WorkItemTypeConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserWorkItemTypeSummary> list(@PathVariable UUID spaceId, Authentication authentication) {
        return service.userSummaries((CurrentUser) authentication.getPrincipal(), spaceId).stream()
            .map(WorkItemTypeApiDtos::userSummary)
            .toList();
    }
}
