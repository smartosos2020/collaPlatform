package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProjectSpaceMigrationModels {
    public static final String BATCH_STATUS_PENDING = "pending";
    public static final String BATCH_STATUS_RUNNING = "running";
    public static final String BATCH_STATUS_COMPLETED = "completed";
    public static final String BATCH_STATUS_FAILED = "failed";
    public static final String BATCH_STATUS_ROLLED_BACK = "rolled_back";

    public static final String MAP_STATUS_ACTIVE = "active";
    public static final String MAP_STATUS_ROLLED_BACK = "rolled_back";

    public static final String FAILURE_SCOPE_MEMBER = "member";
    public static final String FAILURE_SCOPE_PROJECT = "project";
    public static final String PROJECT_FAILURE_NO_VALID_OWNER = "NO_VALID_OWNER";
    public static final String PROJECT_FAILURE_UNIT_FAILED = "UNIT_FAILED";
    public static final String PROJECT_FAILURE_ROLLBACK_FAILED = "ROLLBACK_FAILED";

    public static final String MISMATCH_SPACE_MISSING = "SPACE_MISSING";
    public static final String MISMATCH_SPACE_NOT_ACTIVE = "SPACE_NOT_ACTIVE";
    public static final String MISMATCH_SPACE_ID = "SPACE_ID_MISMATCH";
    public static final String MISMATCH_MEMBER_SET = "MEMBER_SET_MISMATCH";
    public static final String MISMATCH_PLAN_MISSING = "PLAN_MISSING";
    public static final String MISMATCH_MAP_MISSING = "MAP_MISSING";
    public static final String MISMATCH_MAP_UNEXPECTED = "MAP_UNEXPECTED";
    public static final String MISMATCH_MAP_SUPERSEDED = "MAP_SUPERSEDED";

    public static final String RESOLVE_STATUS_MAPPED = "mapped";
    public static final String RESOLVE_STATUS_UNMIGRATED = "unmigrated";
    public static final String RESOLVE_STATUS_FAILED = "failed";
    public static final String RESOLVE_STATUS_UNAVAILABLE = "unavailable";

    private ProjectSpaceMigrationModels() {
    }

    public record MigrationBatchRecord(
        UUID id,
        UUID workspaceId,
        String status,
        boolean dryRun,
        Instant sourceWatermark,
        String sourceChecksum,
        String resultChecksum,
        Map<String, Object> summary,
        List<Map<String, Object>> failures,
        UUID startedBy,
        Instant startedAt,
        Instant finishedAt,
        UUID rolledBackBy,
        Instant rolledBackAt
    ) {
    }

    public record LegacySpaceMapRecord(
        UUID id,
        UUID workspaceId,
        UUID legacyProjectId,
        UUID spaceId,
        int mappingVersion,
        String mappingStatus,
        String sourceChecksum,
        UUID batchId,
        UUID mappedBy,
        Instant mappedAt,
        UUID rolledBackBy,
        Instant rolledBackAt
    ) {
    }

    public record VerifiedSpaceMatch(
        UUID projectId,
        UUID spaceId
    ) {
    }

    public record VerificationMismatch(
        UUID projectId,
        UUID spaceId,
        String differenceType,
        String expected,
        String actual
    ) {
    }

    public record MigrationVerificationReport(
        UUID batchId,
        UUID workspaceId,
        Instant verifiedAt,
        boolean allMatched,
        List<VerifiedSpaceMatch> matches,
        List<VerificationMismatch> mismatches
    ) {
    }

    public record LegacySpaceResolution(
        String status,
        UUID spaceId
    ) {
    }
}
