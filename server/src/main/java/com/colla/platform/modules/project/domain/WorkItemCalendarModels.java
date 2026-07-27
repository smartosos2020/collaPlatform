package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkItemCalendarModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_WINDOW_DAYS = 62;
    public static final int MAX_EVENTS = 100;
    public static final int MAX_OVERLAP_LANES = 8;
    public static final int MAX_PROJECTION_CONTAINERS = MAX_EVENTS + MAX_WINDOW_DAYS + 1;

    private WorkItemCalendarModels() {
    }

    public record DateBinding(
        String startField,
        String endField,
        boolean allDay
    ) {
    }

    public record RangeWindow(
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        String mode
    ) {
    }

    public record CalendarRequest(
        int schemaVersion,
        String viewKey,
        DateBinding binding,
        RangeWindow window,
        QueryDefinition query
    ) {
    }

    public record CalendarEvent(
        UUID workItemId,
        String displayKey,
        String title,
        long workItemVersion,
        String startValue,
        String endValue,
        Instant startInstant,
        Instant endInstant,
        LocalDate displayStartDate,
        LocalDate displayEndDate,
        boolean allDay,
        int overlapLane,
        List<String> availableActions
    ) {
    }

    public record CalendarDay(
        LocalDate date,
        List<CalendarEvent> events
    ) {
    }

    public record CalendarResult(
        int schemaVersion,
        String viewKey,
        String queryHash,
        DateBinding binding,
        RangeWindow window,
        List<CalendarDay> days,
        List<CalendarEvent> noDateEvents,
        int visibleEventCount,
        String nextCursor,
        boolean candidateBoundReached
    ) {
    }

    public record CalendarPreference(
        String viewKey,
        DateBinding binding,
        String timezone,
        String mode,
        long version,
        Instant updatedAt
    ) {
    }

    public record CalendarPreferenceCommand(
        String requestId,
        long expectedVersion,
        DateBinding binding,
        String timezone,
        String mode
    ) {
    }

    public record DateMutation(
        String requestId,
        long expectedWorkItemVersion,
        String operation,
        String startValue,
        String endValue,
        String timezone
    ) {
    }

    public record DateMutationResult(
        UUID workItemId,
        String viewKey,
        String startValue,
        String endValue,
        long workItemVersion,
        boolean replayed
    ) {
    }

    public record WindowIndexEntry(
        UUID workItemId,
        long sourceWorkItemVersion,
        LocalDate startDate,
        LocalDate endDate,
        boolean allDay
    ) {
    }
}
