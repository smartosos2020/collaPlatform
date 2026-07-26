package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemMigrationModels.LegacyMapTarget;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationBatch;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationFailure;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationPlan;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationPlanUnit;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationUnit;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationVerification;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.TypeBinding;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemMigrationRepository {
    List<UUID> listLegacyProjectIds(UUID workspaceId);

    Optional<UUID> findActiveSpace(UUID workspaceId, UUID legacyProjectId);

    JsonNode loadManifest(UUID workspaceId, UUID legacyProjectId);

    Optional<TypeBinding> findTypeBinding(UUID workspaceId, UUID spaceId, String typeKey);

    UUID insertPlan(UUID workspaceId, MigrationPlan plan, boolean dryRun, int throttleMillis, UUID actorId);

    Optional<MigrationBatch> findBatch(UUID workspaceId, UUID batchId);

    List<MigrationBatch> listBatches(UUID workspaceId);

    List<MigrationUnit> listUnits(UUID workspaceId, UUID batchId);

    List<MigrationFailure> listFailures(UUID workspaceId, UUID batchId);

    Optional<MigrationPlanUnit> loadUnitManifest(UUID workspaceId, UUID batchId, UUID unitId);

    Lease acquireLease(UUID workspaceId, UUID batchId, String owner, Instant staleBefore);

    void releaseLease(UUID workspaceId, UUID batchId, UUID token, long fenceVersion);

    boolean heartbeat(UUID workspaceId, UUID batchId, UUID token, long fenceVersion);

    void changeBatchStatus(
        UUID workspaceId,
        UUID batchId,
        String expectedStatus,
        String status,
        String pausedReason,
        UUID token,
        long fenceVersion
    );

    void requestPause(UUID workspaceId, UUID batchId, String reason);

    Optional<MigrationUnit> claimNextUnit(
        UUID workspaceId,
        UUID batchId,
        UUID token,
        long fenceVersion
    );

    void completeUnit(
        UUID workspaceId,
        UUID unitId,
        long fenceVersion,
        int migratedObjects
    );

    void failUnit(
        UUID workspaceId,
        UUID unitId,
        long fenceVersion,
        String errorCode
    );

    void appendFailure(
        UUID workspaceId,
        UUID batchId,
        UUID unitId,
        String code,
        String sourceType,
        UUID sourceId,
        JsonNode safeDetail
    );

    Optional<LegacyMapTarget> findActiveMap(UUID workspaceId, String sourceType, UUID sourceId);

    long nextNumber(UUID workspaceId, UUID spaceId, UUID typeId);

    void insertMigratedWorkItem(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        TypeBinding binding,
        long itemNumber,
        String displayKey,
        String title,
        JsonNode fieldValues,
        String status,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Instant archivedAt
    );

    void insertMap(
        UUID workspaceId,
        UUID batchId,
        UUID unitId,
        String sourceType,
        UUID sourceId,
        UUID sourceProjectId,
        UUID spaceId,
        UUID workItemId,
        String identityDecision,
        String sourceFingerprint
    );

    void upsertParticipant(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID userId,
        String role,
        UUID actorId,
        Instant occurredAt
    );

    void insertComment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID authorId,
        String content,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    );

    void insertAttachment(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID fileId,
        UUID createdBy,
        Instant createdAt
    );

    void insertActivity(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String activityType,
        UUID actorId,
        JsonNode payload,
        Instant occurredAt
    );

    void insertProvenance(
        UUID workspaceId,
        UUID batchId,
        UUID unitId,
        String sourceType,
        UUID sourceId,
        UUID sourceProjectId,
        String checksum,
        String targetType,
        UUID targetId,
        JsonNode safePayload
    );

    VerificationObservation observeBatch(UUID workspaceId, UUID batchId);

    VerificationObservation observeWorkspace(UUID workspaceId);

    MigrationVerification appendVerification(
        UUID workspaceId,
        UUID batchId,
        String scope,
        String status,
        String manifestFingerprint,
        VerificationObservation observation,
        UUID actorId
    );

    boolean hasCanonicalWrites(UUID workspaceId, UUID batchId);

    List<UUID> listActiveTargets(UUID workspaceId, UUID batchId);

    int rollbackBatch(UUID workspaceId, UUID batchId, UUID actorId);

    void enableKillSwitch(UUID workspaceId, UUID actorId);

    record Lease(UUID token, long fenceVersion) {
    }

    record VerificationObservation(
        String fingerprint,
        long expectedSources,
        long activeMaps,
        long targetItems,
        long provenanceRows,
        long comments,
        long attachments,
        long mismatches
    ) {
    }
}
