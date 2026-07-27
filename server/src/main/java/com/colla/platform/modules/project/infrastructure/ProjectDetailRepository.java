package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectDetailModels.DetailPreference;
import com.colla.platform.modules.project.domain.ProjectDetailModels.HealthStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDetailRepository {
    Optional<DetailPreference> findPreference(
        UUID workspaceId, UUID spaceId, UUID actorId
    );

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId
    );

    DetailPreference savePreference(
        UUID workspaceId, UUID spaceId, UUID actorId, String requestId,
        String requestHash, long expectedVersion, List<String> visibleSections,
        boolean compact
    );

    void recordProjection(
        UUID workspaceId, UUID spaceId, UUID actorId,
        HealthStatus health, String sourceFingerprint
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
