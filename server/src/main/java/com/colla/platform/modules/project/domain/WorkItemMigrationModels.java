package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkItemMigrationModels {
    private WorkItemMigrationModels() {
    }

    public record MigrationPlan(
        UUID workspaceId,
        Instant sourceWatermark,
        String sourceFingerprint,
        String planFingerprint,
        List<MigrationPlanUnit> units,
        List<MigrationFailure> failures
    ) {
    }

    public record MigrationPlanUnit(
        UUID legacyProjectId,
        UUID spaceId,
        String sourceFingerprint,
        JsonNode manifest,
        int objectCount
    ) {
    }

    public record MigrationBatch(
        UUID id,
        UUID workspaceId,
        String status,
        Instant sourceWatermark,
        String sourceFingerprint,
        String manifestFingerprint,
        JsonNode plan,
        String planFingerprint,
        long version,
        String leaseOwner,
        UUID leaseToken,
        long fenceVersion,
        Instant heartbeatAt,
        int throttleMillis,
        String pausedReason,
        UUID initiatedBy,
        Instant initiatedAt,
        Instant finishedAt,
        List<MigrationUnit> units,
        List<MigrationFailure> failures
    ) {
    }

    public record MigrationUnit(
        UUID id,
        UUID batchId,
        UUID legacyProjectId,
        UUID spaceId,
        String status,
        int attempt,
        String sourceFingerprint,
        long fenceVersion,
        String lastErrorCode,
        int migratedObjects,
        Instant startedAt,
        Instant finishedAt
    ) {
    }

    public record MigrationFailure(
        UUID id,
        UUID batchId,
        UUID unitId,
        String failureCode,
        String sourceType,
        UUID sourceId,
        Map<String, Object> safeDetail,
        Instant recordedAt
    ) {
        public static MigrationFailure planned(
            String code,
            String sourceType,
            UUID sourceId,
            Map<String, Object> detail
        ) {
            return new MigrationFailure(
                UUID.randomUUID(), null, null, code, sourceType, sourceId, detail, Instant.now()
            );
        }
    }

    public record MigrationExecution(
        MigrationBatch batch,
        int completedUnits,
        int failedUnits,
        int migratedObjects
    ) {
    }

    public record MigrationVerification(
        UUID id,
        UUID workspaceId,
        UUID batchId,
        String scope,
        boolean matched,
        String manifestFingerprint,
        String observedFingerprint,
        Map<String, Object> summary,
        Instant verifiedAt
    ) {
    }

    public record TypeBinding(
        UUID typeId,
        UUID versionId,
        String typeKey,
        String configHash
    ) {
    }

    public record LegacyMapTarget(
        UUID id,
        UUID workItemId,
        String status,
        UUID batchId,
        UUID unitId
    ) {
    }
}
