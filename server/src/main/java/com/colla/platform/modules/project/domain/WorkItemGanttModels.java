package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutation;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutationResult;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.RangeWindow;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkItemGanttModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ROWS = 100;
    public static final int MAX_DEPENDENCIES = 200;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_EXPANDED = 64;

    private WorkItemGanttModels() {
    }

    public record GanttRequest(
        int schemaVersion,
        String viewKey,
        DateBinding binding,
        RangeWindow window,
        QueryDefinition query,
        String hierarchyRelationKey,
        List<UUID> expandedNodeIds,
        boolean criticalPath
    ) {
    }

    public record ScheduleBar(
        UUID workItemId,
        String displayKey,
        String title,
        long workItemVersion,
        LocalDate startDate,
        LocalDate endDate,
        boolean allDay,
        boolean critical,
        long totalFloatDays,
        List<String> availableActions
    ) {
    }

    public record HierarchyRow(
        UUID workItemId,
        UUID parentWorkItemId,
        int depth,
        boolean expandable,
        boolean expanded,
        ScheduleBar bar
    ) {
    }

    public record DependencyLine(
        UUID relationId,
        String relationKey,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        long relationVersion,
        boolean critical
    ) {
    }

    public record GanttResult(
        int schemaVersion,
        String viewKey,
        String queryHash,
        DateBinding binding,
        RangeWindow window,
        List<HierarchyRow> rows,
        List<DependencyLine> dependencies,
        boolean criticalPathAvailable,
        String criticalPathReason,
        boolean truncated
    ) {
    }

    public record GanttPreference(
        String viewKey,
        DateBinding binding,
        String timezone,
        String zoom,
        String hierarchyRelationKey,
        List<UUID> expandedNodeIds,
        long version,
        Instant updatedAt
    ) {
    }

    public record GanttPreferenceCommand(
        String requestId,
        long expectedVersion,
        DateBinding binding,
        String timezone,
        String zoom,
        String hierarchyRelationKey,
        List<UUID> expandedNodeIds
    ) {
    }

    public record GanttDateMutation(
        DateMutation mutation
    ) {
    }

    public record GanttDateMutationResult(
        DateMutationResult result
    ) {
    }

    public record ScheduleIndexEntry(
        UUID workItemId,
        long sourceWorkItemVersion,
        LocalDate startDate,
        LocalDate endDate,
        UUID parentWorkItemId,
        int depth
    ) {
    }
}
