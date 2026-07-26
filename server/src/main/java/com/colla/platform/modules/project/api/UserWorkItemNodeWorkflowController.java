package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemService;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCommandResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeArtifactInput;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskContext;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowPresentation;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeRecoveryResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationRun;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/project-spaces/{spaceId}/work-items/{workItemId}/node-workflow")
public final class UserWorkItemNodeWorkflowController {
    private final WorkItemService service;

    public UserWorkItemNodeWorkflowController(WorkItemService service) {
        this.service = service;
    }

    @GetMapping
    public NodeWorkflowPresentation workflow(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        return service.nodeWorkflow(currentUser(authentication), spaceId, workItemId);
    }

    @GetMapping("/history")
    public NodeHistoryPage history(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @RequestParam(required = false) Long beforeSequence,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        List<NodeHistoryEntry> items = service.nodeWorkflowHistory(
            currentUser(authentication), spaceId, workItemId, beforeSequence, limit
        );
        Long next = items.isEmpty() ? null : items.get(items.size() - 1).sequenceNumber();
        return new NodeHistoryPage(items, next);
    }

    @GetMapping("/tasks/{taskId}")
    public NodeTaskContext taskContext(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable UUID taskId,
        Authentication authentication
    ) {
        return service.nodeTaskContext(
            currentUser(authentication), spaceId, workItemId, taskId
        );
    }

    @PostMapping(":start")
    public NodeCommandResult start(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody StartNodeWorkflowRequest request,
        Authentication authentication
    ) {
        return service.startNodeWorkflow(
            currentUser(authentication), spaceId, workItemId, request.expectedWorkItemVersion(),
            RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/tasks/{taskId}/actions/{operation}")
    public NodeCommandResult taskAction(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable UUID taskId,
        @PathVariable String operation,
        @Valid @RequestBody NodeTaskActionRequest request,
        Authentication authentication
    ) {
        return service.executeNodeTask(
            currentUser(authentication), spaceId, workItemId, taskId, operation,
            request.decision(), request.targetAssigneeId(), request.fieldPatch(), request.artifacts(),
            request.expectedWorkItemVersion(),
            request.expectedInstanceVersion(), RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/recoveries/{commandKey}")
    public NodeRecoveryResult recover(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable String commandKey,
        @Valid @RequestBody NodeRecoveryRequest request,
        Authentication authentication
    ) {
        return service.recoverNodeWorkflow(
            currentUser(authentication), spaceId, workItemId, commandKey,
            request.reason(), request.confirmation(), request.expectedWorkItemVersion(),
            request.expectedInstanceVersion(), RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping(":upgrade")
    public NodeCommandResult upgrade(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody NodeUpgradeRequest request,
        Authentication authentication
    ) {
        return service.upgradeNodeWorkflow(
            currentUser(authentication), spaceId, workItemId,
            request.targetTypeVersionId(), request.nodeMap(), request.reason(),
            request.confirmation(), request.expectedWorkItemVersion(),
            request.expectedInstanceVersion(), RequestBoundaryContext.current().requestId()
        );
    }

    @PostMapping("/compensations/{runId}:resume")
    public NodeCompensationRun resumeCompensation(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable UUID runId,
        @Valid @RequestBody NodeCompensationResumeRequest request,
        Authentication authentication
    ) {
        return service.resumeNodeCompensation(
            currentUser(authentication), spaceId, workItemId, runId,
            request.reason(), request.confirmation()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record StartNodeWorkflowRequest(@PositiveOrZero long expectedWorkItemVersion) {
    }

    public record NodeTaskActionRequest(
        @PositiveOrZero long expectedWorkItemVersion,
        @PositiveOrZero long expectedInstanceVersion,
        String decision,
        UUID targetAssigneeId,
        JsonNode fieldPatch,
        List<NodeArtifactInput> artifacts
    ) {
        public NodeTaskActionRequest {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record NodeHistoryPage(List<NodeHistoryEntry> items, Long nextBeforeSequence) {
    }

    public record NodeRecoveryRequest(
        @PositiveOrZero long expectedWorkItemVersion,
        @PositiveOrZero long expectedInstanceVersion,
        @NotBlank @Size(min = 10, max = 500) String reason,
        @NotBlank String confirmation
    ) {
    }

    public record NodeUpgradeRequest(
        @PositiveOrZero long expectedWorkItemVersion,
        @PositiveOrZero long expectedInstanceVersion,
        @NotNull UUID targetTypeVersionId,
        @NotNull JsonNode nodeMap,
        @NotBlank @Size(min = 10, max = 500) String reason,
        @NotBlank String confirmation
    ) {
    }

    public record NodeCompensationResumeRequest(
        @NotBlank @Size(min = 10, max = 500) String reason,
        @NotBlank String confirmation
    ) {
    }
}
