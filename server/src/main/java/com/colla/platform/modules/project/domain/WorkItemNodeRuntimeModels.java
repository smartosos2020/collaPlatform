package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.BranchDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.EdgeDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.JoinDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.NodeDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.RecoveryCommandDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.CompensationDefinition;
import com.colla.platform.modules.project.domain.WorkItemNodeFlowModels.StageDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkItemNodeRuntimeModels {
    private WorkItemNodeRuntimeModels() {
    }

    public record RuntimeNodeFlow(
        String availability,
        String policyVersion,
        NodeDefinition startNode,
        Map<String, StageDefinition> stages,
        Map<String, NodeDefinition> nodes,
        Map<String, EdgeDefinition> edges,
        Map<String, BranchDefinition> branchesByNode,
        Map<String, JoinDefinition> joinsByNode,
        Map<String, RecoveryCommandDefinition> recoveryCommands,
        Map<String, List<CompensationDefinition>> compensationsByCommand,
        Map<String, List<EdgeDefinition>> outgoing,
        Map<String, List<EdgeDefinition>> incoming
    ) {
        public boolean configured() {
            return "available".equals(availability);
        }
    }

    public record NodeWorkflowInstance(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        String status,
        long workItemVersion,
        long aggregateVersion,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt
    ) {
    }

    public record NodeToken(
        UUID id,
        UUID instanceId,
        String nodeKey,
        String stageKey,
        String status,
        UUID parentTokenId,
        String splitKey,
        String joinKey,
        String correlationKey,
        long aggregateVersion,
        Instant enteredAt,
        Instant completedAt
    ) {
    }

    public record NodeTask(
        UUID id,
        UUID instanceId,
        UUID tokenId,
        String nodeKey,
        String assignmentStrategy,
        List<String> candidateRoles,
        List<UUID> candidateUserIds,
        Integer quorumCount,
        JsonNode formSnapshot,
        JsonNode artifactPolicySnapshot,
        String status,
        UUID assigneeId,
        long aggregateVersion,
        Instant createdAt,
        Instant plannedStartAt,
        Instant dueAt,
        Instant timedOutAt,
        Instant escalatedAt,
        Instant claimedAt,
        Instant completedAt
    ) {
        public NodeTask(
            UUID id,
            UUID instanceId,
            UUID tokenId,
            String nodeKey,
            String assignmentStrategy,
            List<String> candidateRoles,
            Integer quorumCount,
            String status,
            UUID assigneeId,
            long aggregateVersion,
            Instant createdAt,
            Instant claimedAt,
            Instant completedAt
        ) {
            this(
                id, instanceId, tokenId, nodeKey, assignmentStrategy, candidateRoles, List.of(),
                quorumCount, null, null, status, assigneeId, aggregateVersion, createdAt,
                null, null, null, null, claimedAt, completedAt
            );
        }
    }

    public record NodeVote(
        UUID id,
        UUID taskId,
        UUID voterId,
        String decision,
        UUID supersedesVoteId,
        long sequenceNumber,
        Instant occurredAt
    ) {
    }

    public record NodeJoin(
        UUID id,
        UUID instanceId,
        String joinKey,
        String nodeKey,
        String correlationKey,
        String policy,
        int expectedCount,
        Integer quorumCount,
        int arrivedCount,
        String status,
        long aggregateVersion
    ) {
    }

    public record NodeAvailableAction(
        String actionKey,
        UUID taskId,
        String nodeKey,
        String reasonCode,
        long expectedWorkItemVersion,
        long expectedInstanceVersion,
        String policyVersion
    ) {
    }

    public record NodeTaskView(
        UUID id,
        UUID tokenId,
        String nodeKey,
        String assignmentStrategy,
        String status,
        UUID assigneeId,
        long aggregateVersion,
        Instant createdAt,
        Instant plannedStartAt,
        Instant dueAt,
        Instant timedOutAt
    ) {
    }

    public record NodeArtifactInput(
        String artifactKey,
        String kind,
        UUID fileId,
        String objectType,
        UUID objectId
    ) {
    }

    public record NodeTaskArtifact(
        UUID id,
        UUID taskId,
        String artifactKey,
        String kind,
        UUID fileId,
        String objectType,
        UUID objectId,
        UUID createdBy,
        Instant createdAt
    ) {
    }

    public record NodeTaskContext(
        NodeTaskView task,
        JsonNode form,
        JsonNode values,
        JsonNode artifactPolicy,
        List<NodeTaskArtifact> artifacts,
        int candidateCount,
        List<NodeAvailableAction> availableActions
    ) {
    }

    public record NodeTaskInboxItem(
        UUID taskId,
        UUID workItemId,
        String workItemTitle,
        String nodeKey,
        String status,
        UUID assigneeId,
        Instant dueAt,
        Instant createdAt
    ) {
    }

    public record NodeTaskInboxPage(List<NodeTaskInboxItem> items, UUID nextCursor) {
    }

    public record DueNodeTask(
        UUID taskId,
        UUID instanceId,
        UUID workItemId,
        String nodeKey,
        Instant dueAt
    ) {
    }

    public record NodeTokenView(
        UUID id,
        String nodeKey,
        String stageKey,
        String status,
        Instant enteredAt
    ) {
    }

    public record NodeWorkflowPresentation(
        String capability,
        String policyVersion,
        UUID instanceId,
        String status,
        long workItemVersion,
        long aggregateVersion,
        List<NodeTokenView> activeTokens,
        List<NodeTaskView> tasks,
        List<NodeAvailableAction> availableActions
    ) {
    }

    public record NodeCommandResult(
        UUID workItemId,
        UUID instanceId,
        UUID taskId,
        String operation,
        String nodeKey,
        String instanceStatus,
        long workItemVersion,
        long aggregateVersion,
        boolean replayed
    ) {
    }

    public record NodeRecoveryResult(
        NodeCommandResult command,
        UUID compensationRunId,
        int compensationSteps
    ) {
    }

    public record NodeCompensationRun(
        UUID id,
        UUID instanceId,
        UUID commandId,
        String commandKey,
        String status,
        int nextStep,
        int totalSteps,
        String failureCode
    ) {
    }

    public record NodeCompensationStep(
        UUID id,
        UUID runId,
        String compensationKey,
        String actionKey,
        int sortOrder,
        String status,
        int attemptCount,
        String failureCode
    ) {
    }

    public record NodeBackfillBatch(
        UUID id,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetEntryNodeKey,
        int requestedCount,
        int completedCount,
        int failedCount,
        String status
    ) {
    }

    public record NodeBackfillFailure(UUID workItemId, String code, String message) {
    }

    public record NodeBackfillVerification(
        UUID batchId,
        String status,
        int verifiedCount,
        List<NodeBackfillFailure> failures
    ) {
    }

    public record NodeHistoryEntry(
        UUID id,
        long sequenceNumber,
        String eventKind,
        String nodeKey,
        UUID tokenId,
        UUID taskId,
        UUID actorId,
        String actorClass,
        String decisionReference,
        String correlationId,
        JsonNode publicPayload,
        Instant occurredAt
    ) {
    }
}
