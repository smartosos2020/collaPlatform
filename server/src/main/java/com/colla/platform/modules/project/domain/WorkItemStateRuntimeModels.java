package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.ActionDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.GuardDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.StateDefinition;
import com.colla.platform.modules.project.domain.WorkItemStateFlowModels.TransitionDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkItemStateRuntimeModels {
    private WorkItemStateRuntimeModels() {
    }

    public record RuntimeFlow(
        String availability,
        String policyVersion,
        StateDefinition initialState,
        Map<String, StateDefinition> states,
        Map<String, ActionDefinition> actions,
        Map<String, GuardDefinition> guards,
        List<TransitionDefinition> transitions
    ) {
        public boolean configured() {
            return "available".equals(availability);
        }
    }

    public record CurrentState(
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String configHash,
        String stateKey,
        long workItemVersion,
        long aggregateVersion,
        Instant initializedAt,
        Instant updatedAt
    ) {
    }

    public record DecisionContext(
        String spaceRole,
        Set<String> participantRoles,
        JsonNode fieldValues
    ) {
    }

    public record ActionDecision(
        String actionKey,
        boolean allowed,
        String reasonCode,
        ActionDefinition action,
        TransitionDefinition transition
    ) {
        public static ActionDecision denied(String actionKey, String reasonCode) {
            return new ActionDecision(actionKey, false, reasonCode, null, null);
        }
    }

    public record AvailableAction(
        String actionKey,
        String label,
        String kind,
        List<String> requiredFieldKeys,
        int sortOrder,
        String policyVersion
    ) {
    }

    public record WorkflowPresentation(
        String capability,
        String policyVersion,
        String currentStateKey,
        String currentStateLabel,
        String currentStateCategory,
        long aggregateVersion,
        List<AvailableAction> availableActions
    ) {
    }

    public record WorkflowHistoryEntry(
        UUID id,
        long sequenceNumber,
        String fromStateKey,
        String toStateKey,
        String actionKey,
        String actionKind,
        UUID actorId,
        String decisionReference,
        String correlationId,
        Instant occurredAt
    ) {
    }

    public record WorkflowCommandResult(
        UUID workItemId,
        String actionKey,
        String fromStateKey,
        String toStateKey,
        long workItemVersion,
        long aggregateVersion,
        boolean replayed
    ) {
    }

    public record WorkflowBindingCommandResult(
        UUID workItemId,
        UUID fromTypeVersionId,
        UUID toTypeVersionId,
        String fromStateKey,
        String toStateKey,
        long workItemVersion,
        long aggregateVersion,
        boolean replayed
    ) {
    }

    public record StateBackfillBatch(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID targetTypeVersionId,
        String targetConfigHash,
        String targetStateKey,
        String status,
        int requestedCount,
        int completedCount,
        int failedCount,
        String manifestHash,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record StateBackfillFailure(
        UUID workItemId,
        String errorCode,
        String errorMessage
    ) {
    }

    public record StateBackfillVerification(
        UUID batchId,
        String status,
        int verifiedCount,
        List<StateBackfillFailure> failures
    ) {
    }
}
