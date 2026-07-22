package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ActiveSpaceMap;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacyMemberRow;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacyProjectRow;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.SourceFingerprintRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectLegacyMappingRepository {
    List<LegacyProjectRow> findActiveProjects(UUID workspaceId);

    List<LegacyMemberRow> findActiveMembers(UUID workspaceId);

    List<ActiveSpaceMap> findActiveSpaceMaps(UUID workspaceId);

    List<String> findOccupiedSpaceKeys(UUID workspaceId);

    List<SourceFingerprintRow> findSourceFingerprintRows(UUID workspaceId);

    Optional<SourceFingerprintRow> findSourceFingerprintRow(UUID workspaceId, UUID projectId);

    Instant findSourceWatermark(UUID workspaceId);
}
