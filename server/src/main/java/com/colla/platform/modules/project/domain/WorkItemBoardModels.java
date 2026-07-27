package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemBoardModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_COLUMNS = 12;
    public static final int MAX_SWIMLANES = 24;
    public static final int MAX_CARDS = 100;
    public static final int MAX_WIP_LIMIT = 100;
    public static final int MAX_PRESENTATION_PORT_CALLS = MAX_CARDS * 2;
    public static final int MAX_PROJECTION_CONTAINERS =
        MAX_CARDS + (MAX_COLUMNS * MAX_SWIMLANES);

    private WorkItemBoardModels() {
    }

    public record BoardColumn(
        String key,
        String label,
        int wipLimit,
        String moveKind,
        String moveActionKey
    ) {
    }

    public record BoardRequest(
        int schemaVersion,
        String viewKey,
        String columnField,
        String swimlaneField,
        List<BoardColumn> columns,
        QueryDefinition query
    ) {
    }

    public record BoardAction(
        String kind,
        String actionKey,
        String label,
        String fromStateKey,
        UUID taskId,
        long expectedInstanceVersion
    ) {
    }

    public record BoardCard(
        UUID workItemId,
        String displayKey,
        String title,
        String status,
        long workItemVersion,
        String columnKey,
        String swimlaneKey,
        long rank,
        long orderVersion,
        List<String> availableActions,
        List<BoardAction> moveActions
    ) {
    }

    public record BoardLane(
        String key,
        String label,
        List<BoardCard> cards
    ) {
    }

    public record BoardColumnResult(
        BoardColumn column,
        int visibleCount,
        boolean wipExceeded,
        List<BoardLane> lanes
    ) {
    }

    public record BoardResult(
        int schemaVersion,
        String viewKey,
        String queryHash,
        String columnField,
        String swimlaneField,
        List<BoardColumnResult> columns,
        String nextCursor,
        int evaluatedCandidates,
        boolean candidateBoundReached
    ) {
    }

    public record BoardPreference(
        String viewKey,
        String columnField,
        String swimlaneField,
        List<BoardColumn> columns,
        long version,
        Instant updatedAt
    ) {
    }

    public record BoardPreferenceCommand(
        String requestId,
        long expectedVersion,
        String columnField,
        String swimlaneField,
        List<BoardColumn> columns
    ) {
    }

    public record MoveIntent(
        String requestId,
        long expectedWorkItemVersion,
        long expectedOrderVersion,
        String targetColumnKey,
        String targetSwimlaneKey,
        long rank,
        String kind,
        String actionKey,
        String fromStateKey,
        UUID taskId,
        String nodeOperation,
        long expectedInstanceVersion,
        String decision,
        JsonNode fieldPatch
    ) {
    }

    public record MoveResult(
        UUID workItemId,
        String viewKey,
        String targetColumnKey,
        String targetSwimlaneKey,
        long rank,
        long workItemVersion,
        long workflowVersion,
        long orderVersion,
        boolean replayed
    ) {
    }

    public record BoardOrder(
        UUID workItemId,
        String columnKey,
        String swimlaneKey,
        long rank,
        long sourceWorkItemVersion,
        long version,
        Instant updatedAt
    ) {
    }
}
