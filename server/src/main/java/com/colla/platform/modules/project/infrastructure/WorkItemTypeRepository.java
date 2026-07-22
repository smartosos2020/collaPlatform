package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemTypeRepository {
    Optional<String> findSpaceStatus(UUID workspaceId, UUID spaceId);

    Optional<WorkItemTypeDefinition> findById(UUID workspaceId, UUID spaceId, UUID typeId);

    Optional<WorkItemTypeDefinition> findByKey(UUID workspaceId, UUID spaceId, String typeKey);

    List<WorkItemTypeDefinition> listBySpace(UUID workspaceId, UUID spaceId, String status);

    List<WorkItemTypeVersion> listVersions(UUID workspaceId, UUID spaceId, UUID typeId);

    WorkItemTypeCounts countByStatus(UUID workspaceId, UUID spaceId);

    Map<UUID, WorkItemTypeCounts> countBySpaces(UUID workspaceId, List<UUID> spaceIds);

    List<PresetSpaceTarget> listActivePresetSpaces();

    Optional<PresetSpaceTarget> lockPresetSpace(UUID workspaceId, UUID spaceId);

    void insertDefinition(NewDefinition definition);

    void insertVersion(NewVersion version);

    int updateDisplay(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String name,
        String icon,
        String description,
        UUID actorId,
        long expectedAggregateVersion
    );

    int transitionStatus(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String targetStatus,
        UUID actorId,
        long expectedAggregateVersion
    );

    int updateSortOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        int sortOrder,
        UUID actorId,
        long expectedAggregateVersion
    );

    record NewDefinition(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String typeKey,
        String name,
        String icon,
        String description,
        int sortOrder,
        String status,
        boolean system,
        UUID currentVersionId,
        UUID actorId
    ) {
    }

    record NewVersion(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        int versionNumber,
        String configHash,
        String status,
        JsonNode config,
        UUID actorId
    ) {
    }

    record WorkItemTypeCounts(int total, int active, int disabled, int retired) {
    }

    record PresetSpaceTarget(UUID workspaceId, UUID spaceId, UUID createdBy, String status) {
    }
}
