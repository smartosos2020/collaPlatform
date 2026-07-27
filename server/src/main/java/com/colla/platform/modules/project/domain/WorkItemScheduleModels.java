package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkItemScheduleModels {
    public static final int MAX_BASELINES = 20;
    public static final int MAX_BASELINE_ENTRIES = 100;
    public static final int MAX_TIMELINE_EVENTS = 200;
    public static final int RETENTION_DAYS = 90;

    private WorkItemScheduleModels() {
    }

    public record BaselineCreateCommand(
        int schemaVersion,
        String requestId,
        String name,
        GanttRequest request
    ) {
    }

    public record BaselineDeleteCommand(String requestId, long expectedVersion) {
    }

    public record BaselineSummary(
        UUID id,
        String name,
        String queryHash,
        LocalDate windowStart,
        LocalDate windowEnd,
        long version,
        String status,
        Instant createdAt,
        Instant expiresAt
    ) {
    }

    public record BaselineEntry(
        UUID workItemId,
        long workItemVersion,
        LocalDate startDate,
        LocalDate endDate,
        UUID parentWorkItemId,
        int depth
    ) {
    }

    public record BaselineDependency(
        UUID relationId,
        long relationVersion,
        UUID sourceWorkItemId,
        UUID targetWorkItemId
    ) {
    }

    public record BaselineSnapshot(
        BaselineSummary baseline,
        List<BaselineEntry> entries,
        List<BaselineDependency> dependencies
    ) {
    }

    public record BaselineDiff(
        UUID baselineId,
        List<EntryDiff> entries,
        int addedDependencies,
        int removedDependencies,
        boolean truncated
    ) {
    }

    public record EntryDiff(
        UUID workItemId,
        String change,
        LocalDate baselineStartDate,
        LocalDate currentStartDate,
        LocalDate baselineEndDate,
        LocalDate currentEndDate,
        UUID baselineParentWorkItemId,
        UUID currentParentWorkItemId
    ) {
    }

    public record TimelineRequest(
        int schemaVersion,
        GanttRequest request,
        int limit
    ) {
    }

    public record TimelineEvent(
        UUID id,
        String sourceKind,
        UUID sourceId,
        UUID workItemId,
        String eventType,
        UUID actorId,
        Instant occurredAt
    ) {
    }

    public record TimelineResult(
        List<TimelineEvent> events,
        boolean truncated
    ) {
    }
}
