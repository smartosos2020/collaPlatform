package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemTreeViewService;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.AncestorPath;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreference;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreePreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeRequest;
import com.colla.platform.modules.project.domain.WorkItemTreeViewModels.TreeResult;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}")
public final class UserWorkItemTreeViewController {
    private final WorkItemTreeViewService service;

    public UserWorkItemTreeViewController(WorkItemTreeViewService service) {
        this.service = service;
    }

    @PostMapping("/work-item-tree-views:render")
    public TreeResult render(
        @PathVariable UUID spaceId,
        @RequestBody TreeRequest request,
        Authentication authentication
    ) {
        return service.render(user(authentication), spaceId, request);
    }

    @PostMapping("/work-item-tree-views/{focusId}:ancestors")
    public AncestorPath path(
        @PathVariable UUID spaceId,
        @PathVariable UUID focusId,
        @RequestBody TreeRequest request,
        Authentication authentication
    ) {
        return service.path(user(authentication), spaceId, focusId, request);
    }

    @GetMapping("/work-item-tree-views/preferences/{viewKey}")
    public TreePreference preference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        Authentication authentication
    ) {
        return service.preference(user(authentication), spaceId, viewKey);
    }

    @PutMapping("/work-item-tree-views/preferences/{viewKey}")
    public TreePreference savePreference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @RequestBody TreePreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(user(authentication), spaceId, viewKey, command);
    }

    private CurrentUser user(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
