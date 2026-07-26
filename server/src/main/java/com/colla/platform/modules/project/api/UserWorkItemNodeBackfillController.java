package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemService;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeBackfillVerification;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/node-workflow-backfills")
public final class UserWorkItemNodeBackfillController {
    private final WorkItemService service;

    public UserWorkItemNodeBackfillController(WorkItemService service) {
        this.service = service;
    }

    @PostMapping
    public NodeBackfillBatch create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateNodeBackfillRequest request,
        Authentication authentication
    ) {
        return service.createNodeWorkflowBackfill(
            currentUser(authentication), spaceId, request.typeDefinitionId(),
            request.targetTypeVersionId(), request.targetEntryNodeKey(),
            request.workItemIds(), request.reason(), request.confirmation(),
            RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/{batchId}:resume")
    public NodeBackfillBatch resume(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody ResumeNodeBackfillRequest request,
        Authentication authentication
    ) {
        return service.resumeNodeWorkflowBackfill(
            currentUser(authentication), spaceId, batchId, request.confirmation()
        );
    }

    @GetMapping("/{batchId}:verify")
    public NodeBackfillVerification verify(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        Authentication authentication
    ) {
        return service.verifyNodeWorkflowBackfill(
            currentUser(authentication), spaceId, batchId
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateNodeBackfillRequest(
        @NotNull UUID typeDefinitionId,
        @NotNull UUID targetTypeVersionId,
        @NotBlank String targetEntryNodeKey,
        @NotEmpty @Size(max = 500) List<@NotNull UUID> workItemIds,
        @NotBlank @Size(min = 10, max = 500) String reason,
        @NotBlank String confirmation
    ) {
    }

    public record ResumeNodeBackfillRequest(@NotBlank String confirmation) {
    }
}
