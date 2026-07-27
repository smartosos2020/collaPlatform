package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemSavedViewService;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.CopyCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.CreateCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.DeleteCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.RevokeShareCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedView;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedViewExecution;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.ShareCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.TransferCommand;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.UpdateCommand;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
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
@RequestMapping("/api/project-spaces/{spaceId}/saved-views")
public final class UserWorkItemSavedViewController {
    private final WorkItemSavedViewService service;

    public UserWorkItemSavedViewController(WorkItemSavedViewService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedView> list(@PathVariable UUID spaceId, Authentication authentication) {
        return service.list(user(authentication), spaceId);
    }

    @PostMapping
    public SavedView create(
        @PathVariable UUID spaceId,
        @RequestBody CreateCommand command,
        Authentication authentication
    ) {
        return service.create(user(authentication), spaceId, command);
    }

    @GetMapping("/{viewId}")
    public SavedView get(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        Authentication authentication
    ) {
        return service.get(user(authentication), spaceId, viewId);
    }

    @PatchMapping("/{viewId}")
    public SavedView update(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        @RequestBody UpdateCommand command,
        Authentication authentication
    ) {
        return service.update(user(authentication), spaceId, viewId, command);
    }

    @PostMapping("/{viewId}:copy")
    public SavedView copy(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        @RequestBody CopyCommand command,
        Authentication authentication
    ) {
        return service.copy(user(authentication), spaceId, viewId, command);
    }

    @PostMapping("/{viewId}:share")
    public SavedView share(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        @RequestBody ShareCommand command,
        Authentication authentication
    ) {
        return service.share(user(authentication), spaceId, viewId, command);
    }

    @PostMapping("/{viewId}:revoke")
    public SavedView revoke(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        @RequestBody RevokeShareCommand command,
        Authentication authentication
    ) {
        return service.revoke(user(authentication), spaceId, viewId, command);
    }

    @PostMapping("/{viewId}:transfer")
    public SavedView transfer(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        @RequestBody TransferCommand command,
        Authentication authentication
    ) {
        return service.transfer(user(authentication), spaceId, viewId, command);
    }

    @PostMapping("/{viewId}:delete")
    public SavedView delete(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        @RequestBody DeleteCommand command,
        Authentication authentication
    ) {
        return service.delete(user(authentication), spaceId, viewId, command);
    }

    @PostMapping("/{viewId}:execute")
    public SavedViewExecution execute(
        @PathVariable UUID spaceId,
        @PathVariable UUID viewId,
        Authentication authentication
    ) {
        return service.execute(user(authentication), spaceId, viewId);
    }

    private CurrentUser user(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
