package com.colla.platform.modules.project.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ResourceScheduleModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ROWS = 200;
    public static final int MAX_BARS = 500;
    public static final int MAX_MARKERS = 366;

    private ResourceScheduleModels() {
    }

    public record SavePreferenceCommand(
        int schemaVersion, String requestId, long expectedVersion,
        LocalDate windowStart, LocalDate windowEnd, String zoom
    ) {
    }

    public record AdjustmentCommand(
        int schemaVersion, String requestId, boolean preview,
        UUID allocationId, long expectedVersion,
        LocalDate startDate, LocalDate endDate,
        BigDecimal allocationPercent, String reason
    ) {
    }

    public record SchedulePreference(
        UUID id, LocalDate windowStart, LocalDate windowEnd,
        String zoom, long version, Instant updatedAt
    ) {
    }

    public record AssignmentBar(
        UUID allocationId, UUID workItemId, UUID userId,
        LocalDate startDate, LocalDate endDate,
        BigDecimal allocationPercent, long sourceVersion
    ) {
    }

    public record ConflictMarker(
        UUID userId, LocalDate date, String signal,
        int capacityMinutes, int allocatedMinutes, String explanation
    ) {
    }

    public record ResourceRow(
        UUID userId, int capacityMinutes, int allocatedMinutes,
        int actualMinutes, int conflictCount
    ) {
    }

    public record ResourceSchedule(
        LocalDate windowStart, LocalDate windowEnd, String zoom,
        List<ResourceRow> rows, List<AssignmentBar> bars,
        List<ConflictMarker> conflicts, SchedulePreference preference,
        boolean truncated
    ) {
    }

    public record AdjustmentResult(
        boolean preview, boolean committed, UUID allocationId,
        LocalDate startDate, LocalDate endDate,
        BigDecimal allocationPercent, long version, String provenance
    ) {
    }
}
