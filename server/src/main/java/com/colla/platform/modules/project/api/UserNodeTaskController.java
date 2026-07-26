package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemService;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskInboxPage;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/node-tasks")
public final class UserNodeTaskController {
    private final WorkItemService service;

    public UserNodeTaskController(WorkItemService service) {
        this.service = service;
    }

    @GetMapping
    public NodeTaskInboxPage inbox(
        @PathVariable UUID spaceId,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return service.nodeTaskInbox(currentUser(authentication), spaceId, cursor, limit);
    }

    @PostMapping(":process-due")
    public DueTaskProcessingResult processDue(
        @PathVariable UUID spaceId,
        @RequestParam(defaultValue = "100") int limit,
        Authentication authentication
    ) {
        return new DueTaskProcessingResult(
            service.processDueNodeTasks(currentUser(authentication), spaceId, limit)
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record DueTaskProcessingResult(int processedCount) {
    }
}
