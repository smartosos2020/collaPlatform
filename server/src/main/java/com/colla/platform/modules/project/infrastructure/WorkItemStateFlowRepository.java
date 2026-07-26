package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.CurrentState;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowHistoryEntry;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WorkItemStateFlowRepository {
    boolean tryInitialize(CurrentStateInsert state);

    Optional<CurrentState> findCurrent(UUID workspaceId, UUID spaceId, UUID workItemId);

    Optional<CurrentState> lockCurrent(UUID workspaceId, UUID spaceId, UUID workItemId);

    Map<UUID, CurrentState> findCurrentBatch(UUID workspaceId, UUID spaceId, List<UUID> workItemIds);

    int compareAndSetState(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String expectedStateKey,
        String targetStateKey,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    );

    int alignWorkItemVersion(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        UUID actorId
    );

    int upgradeBinding(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID expectedTypeVersionId,
        String expectedConfigHash,
        String expectedStateKey,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetStateKey,
        long expectedWorkItemVersion,
        long targetWorkItemVersion,
        long expectedAggregateVersion,
        UUID actorId
    );

    boolean tryStartCommand(CommandStart command);

    Optional<CommandReceipt> findCommand(
        UUID workspaceId,
        UUID workItemId,
        String operation,
        String requestId
    );

    void completeCommand(UUID commandId, JsonNode response);

    void appendHistory(HistoryAppend history);

    List<WorkflowHistoryEntry> pageHistory(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        Long beforeSequence,
        int limit
    );

    Set<String> participantRoles(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID actorId
    );

    Map<UUID, Set<String>> participantRolesBatch(
        UUID workspaceId,
        UUID spaceId,
        List<UUID> workItemIds,
        UUID actorId
    );

    record CurrentStateInsert(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        String stateKey,
        long workItemVersion,
        UUID actorId
    ) {
    }

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String operation,
        String actionKey,
        String fromStateKey,
        long expectedWorkItemVersion,
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

    record HistoryAppend(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        long sequenceNumber,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        String fromStateKey,
        String toStateKey,
        String actionKey,
        String actionKind,
        UUID actorId,
        String actorClass,
        String decisionReference,
        String correlationId,
        String causationId,
        JsonNode publicPayload
    ) {
    }
}
