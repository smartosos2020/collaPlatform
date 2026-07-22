package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSpaceMigrationBatchRepository {
    UUID insertBatch(
        UUID workspaceId,
        boolean dryRun,
        Instant sourceWatermark,
        String sourceChecksum,
        UUID startedBy
    );

    Optional<MigrationBatchRecord> findBatch(UUID workspaceId, UUID batchId);

    List<MigrationBatchRecord> listByWorkspace(UUID workspaceId);

    void updateSourceFingerprint(UUID workspaceId, UUID batchId, Instant sourceWatermark, String sourceChecksum);

    void finalizeBatch(
        UUID workspaceId,
        UUID batchId,
        String status,
        String resultChecksum,
        Map<String, Object> summary,
        List<Map<String, Object>> failures
    );

    void markBatchRolledBack(
        UUID workspaceId,
        UUID batchId,
        String status,
        UUID actorId,
        Map<String, Object> summary,
        List<Map<String, Object>> failures
    );

    void updateSummary(UUID workspaceId, UUID batchId, Map<String, Object> summary);

    void lockWorkspaceForMigration(UUID workspaceId);
}
