package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemService;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowBindingCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowPresentation;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/project-spaces/{spaceId}/work-items/{workItemId}/workflow")
public final class UserWorkItemStateFlowController {
    private final WorkItemService service;

    public UserWorkItemStateFlowController(WorkItemService service) {
        this.service = service;
    }

    @GetMapping
    public WorkflowPresentation workflow(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        return service.workflow(currentUser(authentication), spaceId, workItemId);
    }

    @GetMapping("/history")
    public WorkflowHistoryPage history(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @RequestParam(required = false) Long beforeSequence,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        List<WorkflowHistoryEntry> items = service.workflowHistory(
            currentUser(authentication), spaceId, workItemId, beforeSequence, limit
        );
        Long next = items.isEmpty() ? null : items.get(items.size() - 1).sequenceNumber();
        return new WorkflowHistoryPage(items, next);
    }

    @PostMapping("/actions/{actionKey}")
    public WorkflowCommandResult execute(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable String actionKey,
        @Valid @RequestBody ExecuteWorkflowActionRequest request,
        Authentication authentication
    ) {
        return service.executeWorkflowAction(
            currentUser(authentication), spaceId, workItemId, actionKey,
            request.fromStateKey(), request.expectedWorkItemVersion(),
            request.fieldPatch(), RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/corrections")
    public WorkflowCommandResult correct(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody CorrectWorkflowStateRequest request,
        Authentication authentication
    ) {
        return service.correctWorkflowState(
            currentUser(authentication), spaceId, workItemId, request.targetStateKey(),
            request.expectedWorkItemVersion(), request.reason(), request.confirmation(),
            RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/binding-upgrades")
    public WorkflowBindingCommandResult upgradeBinding(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody UpgradeWorkflowBindingRequest request,
        Authentication authentication
    ) {
        return service.upgradeWorkflowBinding(
            currentUser(authentication), spaceId, workItemId, request.targetTypeVersionId(),
            request.targetStateKey(), request.expectedWorkItemVersion(), request.reason(),
            request.confirmation(), RequestBoundaryContext.current().requestId()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record ExecuteWorkflowActionRequest(
        @NotBlank String fromStateKey,
        @PositiveOrZero long expectedWorkItemVersion,
        JsonNode fieldPatch
    ) {
    }

    public record CorrectWorkflowStateRequest(
        @NotBlank String targetStateKey,
        @PositiveOrZero long expectedWorkItemVersion,
        @NotBlank String reason,
        @NotBlank String confirmation
    ) {
    }

    public record UpgradeWorkflowBindingRequest(
        @NotNull UUID targetTypeVersionId,
        @NotBlank String targetStateKey,
        @PositiveOrZero long expectedWorkItemVersion,
        @NotBlank String reason,
        @NotBlank String confirmation
    ) {
    }

    public record WorkflowHistoryPage(List<WorkflowHistoryEntry> items, Long nextBeforeSequence) {
    }
}
