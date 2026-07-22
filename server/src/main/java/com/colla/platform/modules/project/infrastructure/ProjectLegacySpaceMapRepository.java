package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.LegacySpaceMapRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectLegacySpaceMapRepository {
    void insertMap(
        UUID workspaceId,
        UUID legacyProjectId,
        UUID spaceId,
        int mappingVersion,
        UUID batchId,
        String sourceChecksum,
        UUID mappedBy
    );

    Optional<LegacySpaceMapRecord> findActiveByProject(UUID workspaceId, UUID legacyProjectId);

    Optional<LegacySpaceMapRecord> findByProject(UUID workspaceId, UUID legacyProjectId);

    List<LegacySpaceMapRecord> findByBatch(UUID workspaceId, UUID batchId);

    List<LegacySpaceMapRecord> findActiveByWorkspace(UUID workspaceId);

    void markRolledBack(UUID workspaceId, UUID mapId, UUID actorId);
}
