package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemBoardService;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreference;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardRequest;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardResult;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.MoveIntent;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.MoveResult;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.Optional;
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
public final class UserWorkItemBoardController {
    private final WorkItemBoardService service;

    public UserWorkItemBoardController(WorkItemBoardService service) {
        this.service = service;
    }

    @PostMapping("/work-item-boards:render")
    public BoardResult render(
        @PathVariable UUID spaceId,
        @Valid @RequestBody BoardRequest request,
        Authentication authentication
    ) {
        return service.render(currentUser(authentication), spaceId, request);
    }

    @GetMapping("/work-item-boards/{viewKey}/preference")
    public Optional<BoardPreference> preference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        Authentication authentication
    ) {
        return service.preference(currentUser(authentication), spaceId, viewKey);
    }

    @PutMapping("/work-item-boards/{viewKey}/preference")
    public BoardPreference savePreference(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @Valid @RequestBody BoardPreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(currentUser(authentication), spaceId, viewKey, command);
    }

    @PostMapping("/work-item-boards/{viewKey}/items/{workItemId}:move")
    public MoveResult move(
        @PathVariable UUID spaceId,
        @PathVariable String viewKey,
        @PathVariable UUID workItemId,
        @Valid @RequestBody MoveIntent intent,
        Authentication authentication
    ) {
        return service.move(currentUser(authentication), spaceId, viewKey, workItemId, intent);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
