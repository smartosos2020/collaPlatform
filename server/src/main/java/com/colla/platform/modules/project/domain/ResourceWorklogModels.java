package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ResourceWorklogModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_WORKLOGS = 200;
    public static final int MAX_REVISIONS = 100;

    private ResourceWorklogModels() {
    }

    public record MutateWorklogCommand(
        int schemaVersion,
        String requestId,
        String operation,
        UUID worklogId,
        long expectedVersion,
        UUID workItemId,
        UUID userId,
        LocalDate workDate,
        int durationMinutes,
        String source,
        String reason
    ) {
    }

    public record WorklogRevision(
        UUID id,
        long revisionNumber,
        LocalDate workDate,
        int durationMinutes,
        String source,
        String approvalState,
        String reason,
        UUID actorId,
        Instant createdAt
    ) {
    }

    public record Worklog(
        UUID id,
        UUID workItemId,
        UUID userId,
        LocalDate workDate,
        int durationMinutes,
        String source,
        String approvalState,
        long currentRevision,
        long version,
        UUID updatedBy,
        Instant updatedAt,
        List<WorklogRevision> revisions
    ) {
    }

    public record Variance(
        UUID workItemId,
        String estimateUnit,
        int estimatedMinutes,
        int actualMinutes,
        boolean comparable,
        int varianceMinutes,
        String explanation
    ) {
    }

    public record WorklogFoundation(
        List<Worklog> worklogs,
        List<Variance> variance,
        boolean truncated
    ) {
    }
}
