package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.PresentationConfig;
import com.colla.platform.modules.project.domain.WorkItemSavedViewModels.SavedView;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemSavedViewRepository {
    List<SavedView> listAccessible(UUID workspaceId, UUID spaceId, UUID userId, int limit);

    Optional<SavedView> findAccessible(UUID workspaceId, UUID spaceId, UUID userId, UUID viewId);

    Optional<SavedView> findAny(UUID workspaceId, UUID spaceId, UUID viewId);

    Optional<UUID> findSpaceId(UUID workspaceId, UUID viewId);

    SavedView create(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID ownerUserId,
        String scope,
        String name,
        String description,
        QueryDefinition query,
        PresentationConfig presentation,
        String configHash
    );

    SavedView update(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        String scope,
        String name,
        String description,
        QueryDefinition query,
        PresentationConfig presentation,
        String configHash,
        UUID actorId
    );

    SavedView share(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID subjectUserId,
        String permission,
        UUID actorId
    );

    SavedView revoke(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID subjectUserId,
        UUID actorId
    );

    SavedView transfer(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID newOwnerUserId,
        UUID actorId
    );

    SavedView delete(
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        long expectedVersion,
        UUID actorId
    );

    boolean tryStartCommand(CommandStart start);

    Optional<CommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        String operation,
        String requestId
    );

    void completeCommand(UUID commandId, UUID viewId, JsonNode response);

    record CommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID viewId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion,
        UUID actorId
    ) {
    }

    record CommandReceipt(
        UUID id,
        UUID spaceId,
        UUID viewId,
        String operation,
        String requestId,
        String requestHash,
        long expectedVersion,
        UUID actorId,
        String status,
        JsonNode response
    ) {
    }
}
