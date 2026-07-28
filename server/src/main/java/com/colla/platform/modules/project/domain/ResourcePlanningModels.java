package com.colla.platform.modules.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ResourcePlanningModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_EXCEPTIONS = 366;
    public static final int MAX_ESTIMATES = 200;
    public static final int MAX_SCHEDULE_DAYS = 730;

    private ResourcePlanningModels() {
    }

    public record CalendarExceptionInput(
        UUID id, LocalDate date, int availableMinutes, String note
    ) {
    }

    public record SaveCalendarCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String timezone,
        List<Integer> workDays,
        int dailyMinutes,
        List<CalendarExceptionInput> exceptions
    ) {
    }

    public record SaveEstimateCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        UUID workItemId,
        String unit,
        BigDecimal amount
    ) {
    }

    public record CalendarException(
        UUID id, LocalDate date, int availableMinutes, String note
    ) {
    }

    public record WorkCalendar(
        UUID id,
        String timezone,
        List<Integer> workDays,
        int dailyMinutes,
        List<CalendarException> exceptions,
        long version,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record Estimate(
        UUID id,
        UUID workItemId,
        String unit,
        BigDecimal amount,
        long sourceWorkItemVersion,
        long version,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record ScheduleProjection(
        UUID workItemId,
        long estimateVersion,
        boolean timeComparable,
        int requiredMinutes,
        LocalDate projectedStart,
        LocalDate projectedFinish,
        boolean truncated,
        String explanation
    ) {
    }

    public record PlanningFoundation(
        WorkCalendar calendar,
        List<Estimate> estimates,
        List<ScheduleProjection> schedule
    ) {
    }
}
