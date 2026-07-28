package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.PanoramaPreference;
import java.util.Optional;
import java.util.UUID;

public interface CrossTeamPanoramaPreferenceRepository {
    Optional<PanoramaPreference> find(UUID workspaceId, UUID spaceId, UUID userId);

    PanoramaPreference save(
        UUID workspaceId, UUID spaceId, UUID userId,
        boolean compact, int windowDays, long expectedVersion
    );
}
