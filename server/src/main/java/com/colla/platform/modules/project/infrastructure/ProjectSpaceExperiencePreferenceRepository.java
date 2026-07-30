package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreference;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSpaceExperiencePreferenceRepository {
    Optional<ExperiencePreference> find(UUID workspaceId, UUID spaceId, UUID userId);

    ExperiencePreference save(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        int schemaVersion,
        String mode,
        long expectedVersion
    );

    void reset(UUID workspaceId, UUID spaceId, UUID userId, long expectedVersion);
}
