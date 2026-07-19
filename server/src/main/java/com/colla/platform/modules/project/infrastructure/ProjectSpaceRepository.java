package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectSpaceRepository {
    UUID createSpaceWithOwner(
        UUID workspaceId,
        String spaceKey,
        String name,
        String description,
        String visibility,
        UUID ownerId
    );

    List<ProjectSpaceSummary> listVisible(UUID workspaceId, UUID userId);

    List<ProjectSpaceSummary> listGovernance(
        UUID workspaceId,
        UUID userId,
        String status,
        String visibility,
        boolean includeArchived,
        int limit,
        int offset
    );

    Optional<ProjectSpaceSummary> findById(UUID workspaceId, UUID spaceId, UUID userId);

    Optional<String> findActiveRole(UUID workspaceId, UUID spaceId, UUID userId);

    void updateSpace(UUID workspaceId, UUID spaceId, String name, String description, String visibility, UUID actorId);

    void transitionSpace(UUID workspaceId, UUID spaceId, String targetStatus, UUID actorId);
}
