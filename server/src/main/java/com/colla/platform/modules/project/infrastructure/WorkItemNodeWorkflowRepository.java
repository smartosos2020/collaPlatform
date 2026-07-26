package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeHistoryEntry;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.DueNodeTask;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeJoin;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskArtifact;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTaskInboxItem;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeTask;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeToken;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeVote;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowInstance;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationRun;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCompensationStep;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WorkItemNodeWorkflowRepository {
    boolean tryStartInstance(InstanceStart value);

    Optional<NodeWorkflowInstance> findInstance(UUID workspaceId, UUID spaceId, UUID workItemId);

    Optional<NodeWorkflowInstance> lockInstance(UUID workspaceId, UUID spaceId, UUID workItemId);

    int updateInstance(
        UUID instanceId,
        String expectedStatus,
        String targetStatus,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    );

    int alignWorkItemVersion(
        UUID instanceId,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        UUID actorId
    );

    void insertToken(TokenInsert value);

    Optional<NodeToken> lockToken(UUID workspaceId, UUID spaceId, UUID instanceId, UUID tokenId);

    List<NodeToken> activeTokens(UUID workspaceId, UUID spaceId, UUID instanceId);

    int updateTokenStatus(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID tokenId,
        String expectedStatus,
        String targetStatus
    );

    int closeJoinTokens(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        String nodeKey,
        String correlationKey,
        String targetStatus
    );

    int cancelOpenTasksForCorrelation(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        String correlationKey
    );

    int closeOpenTokensForCorrelation(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        String correlationKey,
        String targetStatus
    );

    int cancelOpenTasks(UUID workspaceId, UUID spaceId, UUID instanceId);

    int cancelOpenTokens(UUID workspaceId, UUID spaceId, UUID instanceId);

    int cancelWaitingJoins(UUID workspaceId, UUID spaceId, UUID instanceId);

    int recoverInstance(
        UUID instanceId,
        String expectedStatus,
        String targetStatus,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    );

    int upgradeInstanceBinding(
        UUID instanceId,
        UUID expectedTypeVersionId,
        String expectedConfigHash,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetStatus,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    );

    void insertTask(TaskInsert value);

    Optional<NodeTask> lockTask(UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId);

    Optional<NodeTask> findTask(UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId);

    List<NodeTask> openTasks(UUID workspaceId, UUID spaceId, UUID instanceId);

    int claimTask(UUID taskId, String expectedStatus, UUID assigneeId, long expectedAggregateVersion);

    int delegateTask(UUID taskId, UUID expectedAssigneeId, UUID targetAssigneeId, long expectedAggregateVersion);

    int transferTask(UUID taskId, UUID targetAssigneeId, long expectedAggregateVersion);

    int completeTask(UUID taskId, long expectedAggregateVersion);

    void insertTaskArtifact(TaskArtifactInsert value);

    List<NodeTaskArtifact> taskArtifacts(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID taskId
    );

    List<NodeVote> votes(UUID workspaceId, UUID spaceId, UUID instanceId, UUID taskId);

    void appendVote(VoteInsert value);

    boolean tryCreateJoin(JoinInsert value);

    Optional<NodeJoin> lockJoin(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        String joinKey,
        String correlationKey
    );

    boolean tryRecordJoinArrival(JoinArrival value);

    int updateJoinArrival(UUID joinId, int expectedArrivedCount, int targetArrivedCount);

    int releaseJoin(UUID joinId, int expectedArrivedCount, long expectedAggregateVersion);

    boolean tryStartCommand(CommandStart value);

    Optional<CommandReceipt> findCommand(
        UUID workspaceId,
        UUID workItemId,
        String operation,
        String requestId
    );

    void completeCommand(UUID commandId, JsonNode response);

    void insertCompensationRun(CompensationRunInsert value);

    void insertCompensationStep(CompensationStepInsert value);

    Optional<NodeCompensationRun> lockCompensationRun(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID runId
    );

    List<NodeCompensationStep> compensationSteps(
        UUID workspaceId, UUID spaceId, UUID instanceId, UUID runId
    );

    int completeCompensationStep(UUID stepId);

    int completeCompensationRun(UUID runId, int totalSteps);

    int markCompensationRunRunning(UUID runId);

    void appendHistory(HistoryAppend value);

    long nextHistorySequence(UUID workspaceId, UUID spaceId, UUID instanceId);

    List<NodeHistoryEntry> pageHistory(
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        Long beforeSequence,
        int limit
    );

    Set<String> participantRoles(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID actorId
    );

    Set<UUID> candidateUserIds(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        List<String> candidateRoles
    );

    Set<UUID> activeMemberUserIds(
        UUID workspaceId,
        UUID spaceId,
        Set<UUID> requestedUserIds
    );

    List<NodeTaskInboxItem> taskInbox(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        boolean includeAll,
        UUID cursor,
        int limit
    );

    List<DueNodeTask> markDueTasksTimedOut(
        UUID workspaceId,
        UUID spaceId,
        java.time.Instant now,
        int limit
    );

    record InstanceStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        long workItemVersion,
        UUID actorId
    ) {
    }

    record TokenInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        String nodeKey,
        String stageKey,
        String status,
        UUID parentTokenId,
        String splitKey,
        String joinKey,
        String correlationKey
    ) {
    }

    record TaskInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID tokenId,
        String nodeKey,
        String assignmentStrategy,
        List<String> candidateRoles,
        List<UUID> candidateUserIds,
        Integer quorumCount,
        JsonNode formSnapshot,
        JsonNode artifactPolicySnapshot,
        java.time.Instant plannedStartAt,
        java.time.Instant dueAt
    ) {
        public TaskInsert(
            UUID id,
            UUID workspaceId,
            UUID spaceId,
            UUID instanceId,
            UUID tokenId,
            String nodeKey,
            String assignmentStrategy,
            List<String> candidateRoles,
            Integer quorumCount
        ) {
            this(
                id, workspaceId, spaceId, instanceId, tokenId, nodeKey, assignmentStrategy,
                candidateRoles, List.of(), quorumCount, null, null, null, null
            );
        }
    }

    record VoteInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID taskId,
        UUID tokenId,
        String nodeKey,
        UUID voterId,
        String decision,
        UUID supersedesVoteId,
        long sequenceNumber,
        JsonNode publicPayload
    ) {
    }

    record TaskArtifactInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID taskId,
        String artifactKey,
        String kind,
        UUID fileId,
        String objectType,
        UUID objectId,
        UUID actorId
    ) {
    }

    record JoinInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        String joinKey,
        String nodeKey,
        String correlationKey,
        String policy,
        int expectedCount,
        Integer quorumCount
    ) {
    }

    record JoinArrival(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID joinId,
        UUID tokenId
    ) {
    }

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID instanceId,
        String operation,
        String nodeKey,
        long expectedWorkItemVersion,
        Long expectedInstanceVersion,
        String requestId,
        String requestHash,
        UUID actorId
    ) {
    }

    record CommandReceipt(
        UUID id,
        String requestHash,
        String status,
        JsonNode response,
        UUID createdBy
    ) {
    }

    record CompensationRunInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID commandId,
        String commandKey,
        int totalSteps,
        UUID actorId
    ) {
    }

    record CompensationStepInsert(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID runId,
        String compensationKey,
        String actionKey,
        int sortOrder
    ) {
    }

    record HistoryAppend(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID instanceId,
        UUID workItemId,
        long sequenceNumber,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        String eventKind,
        String nodeKey,
        UUID tokenId,
        UUID taskId,
        UUID actorId,
        String actorClass,
        String decisionReference,
        String correlationId,
        String causationId,
        JsonNode publicPayload
    ) {
    }
}
