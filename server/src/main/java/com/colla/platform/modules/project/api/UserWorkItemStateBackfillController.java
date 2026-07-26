package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemService;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillBatch;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.StateBackfillVerification;
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
@RequestMapping("/api/project-spaces/{spaceId}/workflow-backfills")
public final class UserWorkItemStateBackfillController {
    private final WorkItemService service;

    public UserWorkItemStateBackfillController(WorkItemService service) {
        this.service = service;
    }

    @PostMapping
    public StateBackfillBatch create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateStateBackfillRequest request,
        Authentication authentication
    ) {
        return service.createWorkflowBackfill(
            currentUser(authentication), spaceId, request.typeDefinitionId(),
            request.targetTypeVersionId(), request.targetStateKey(),
            request.workItemIds(), request.reason(), request.confirmation(),
            RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/{batchId}:resume")
    public StateBackfillBatch resume(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        @Valid @RequestBody ResumeStateBackfillRequest request,
        Authentication authentication
    ) {
        return service.resumeWorkflowBackfill(
            currentUser(authentication), spaceId, batchId, request.confirmation()
        );
    }

    @GetMapping("/{batchId}:verify")
    public StateBackfillVerification verify(
        @PathVariable UUID spaceId,
        @PathVariable UUID batchId,
        Authentication authentication
    ) {
        return service.verifyWorkflowBackfill(
            currentUser(authentication), spaceId, batchId
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateStateBackfillRequest(
        @NotNull UUID typeDefinitionId,
        @NotNull UUID targetTypeVersionId,
        @NotBlank String targetStateKey,
        @NotEmpty @Size(max = 500) List<@NotNull UUID> workItemIds,
        @NotBlank String reason,
        @NotBlank String confirmation
    ) {
    }

    public record ResumeStateBackfillRequest(@NotBlank String confirmation) {
    }
}
