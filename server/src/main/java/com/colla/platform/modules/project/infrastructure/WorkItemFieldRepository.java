package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemFieldRepository {
    Optional<FieldDefinition> findById(UUID workspaceId, UUID spaceId, UUID typeId, UUID fieldId);

    List<FieldDefinition> listByType(UUID workspaceId, UUID spaceId, UUID typeId, String status);

    void insert(NewFieldDefinition definition);

    int update(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String name,
        String description,
        JsonNode config,
        String configHash,
        UUID actorId,
        long expectedAggregateVersion
    );

    int transitionStatus(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String status,
        UUID actorId,
        long expectedAggregateVersion
    );

    int updateSortOrder(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        int sortOrder,
        UUID actorId,
        long expectedAggregateVersion
    );

    record NewFieldDefinition(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        String configHash,
        int sortOrder,
        String status,
        boolean system,
        UUID actorId
    ) {
    }
}
