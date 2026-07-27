package com.colla.platform.modules.project.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemRelationMigrationRepository {
    List<LegacyRelationUnit> inspectLegacy(UUID workspaceId, UUID spaceId);

    void createBatch(MigrationBatch batch);

    void insertUnits(UUID batchId, List<LegacyRelationUnit> units);

    Optional<MigrationBatch> findBatch(
        UUID workspaceId, UUID spaceId, UUID batchId, boolean lock
    );

    Optional<MigrationBatch> findBatchByRequest(
        UUID workspaceId, UUID spaceId, String requestId
    );

    List<MigrationUnit> listUnits(
        UUID workspaceId, UUID spaceId, UUID batchId, List<String> statuses
    );

    int transitionBatch(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        long expectedVersion,
        List<String> expectedStatuses,
        String targetStatus
    );

    void markUnitCompleted(UUID unitId, UUID relationId);

    void markUnitFailed(UUID unitId, String errorCode);

    void markUnitVerified(UUID unitId);

    void markUnitRolledBack(UUID unitId);

    void refreshCounts(UUID workspaceId, UUID spaceId, UUID batchId, String status);

    List<UUID> verificationFailures(UUID workspaceId, UUID spaceId, UUID batchId);

    void appendVerification(
        UUID workspaceId,
        UUID spaceId,
        UUID batchId,
        boolean passed,
        int checkedCount,
        List<UUID> failureUnitIds,
        UUID actorId
    );

    record MigrationBatch(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String relationKey,
        String requestId,
        String manifestHash,
        boolean dryRun,
        String status,
        long version,
        int totalCount,
        int canonicalCount,
        int preservedCount,
        int completedCount,
        int failedCount,
        String reasonHash,
        UUID initiatedBy,
        Instant initiatedAt,
        Instant updatedAt,
        Instant completedAt
    ) {
    }

    record LegacyRelationUnit(
        UUID id,
        UUID sourceRelationId,
        UUID sourceIssueId,
        String targetType,
        UUID targetId,
        String sourceFingerprint,
        String classification,
        UUID sourceWorkItemId,
        UUID targetWorkItemId
    ) {
    }

    record MigrationUnit(
        UUID id,
        UUID sourceRelationId,
        UUID sourceIssueId,
        String targetType,
        UUID targetId,
        String sourceFingerprint,
        String classification,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        UUID relationId,
        String status,
        int attempt,
        String errorCode
    ) {
    }
}
