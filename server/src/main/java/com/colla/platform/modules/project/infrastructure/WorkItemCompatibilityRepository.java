package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.CutoverState;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyWorkItemMap;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemCompatibilityRepository {
    LegacyProfile profile(UUID workspaceId);

    Optional<LegacyWorkItemMap> findMap(UUID workspaceId, String sourceType, UUID sourceId);

    Optional<CutoverState> findCutover(UUID workspaceId, UUID spaceId);

    Optional<UUID> findLegacyProjectSpace(UUID workspaceId, UUID projectId);

    Optional<UUID> findIssueProject(UUID workspaceId, UUID issueId);

    CutoverState changeCutover(
        UUID workspaceId,
        UUID spaceId,
        String readStage,
        boolean legacyWriteEnabled,
        boolean killSwitchEnabled,
        long expectedVersion,
        UUID actorId
    );

    void recordShadowSample(
        UUID workspaceId,
        UUID spaceId,
        String sourceType,
        UUID sourceId,
        String primarySource,
        String legacyFingerprint,
        String canonicalFingerprint,
        String outcome,
        int primaryLatencyMs,
        Integer shadowLatencyMs
    );
}
