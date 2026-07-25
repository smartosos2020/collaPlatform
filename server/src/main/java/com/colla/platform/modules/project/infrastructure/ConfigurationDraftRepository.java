package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDraft;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DraftCommandReceipt;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationDraftRepository {
    Optional<ConfigurationDraft> findActive(UUID workspaceId, UUID spaceId, UUID typeId);

    Optional<ConfigurationDraft> lockActive(UUID workspaceId, UUID spaceId, UUID typeId);

    Optional<ConfigurationDraft> findById(UUID workspaceId, UUID spaceId, UUID typeId, UUID draftId);

    boolean tryInsert(NewDraft draft);

    int update(UpdateDraft draft);

    int abandon(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID draftId,
        UUID actorId,
        long expectedAggregateVersion
    );

    boolean tryStartCommand(DraftCommandStart command);

    Optional<DraftCommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String operation,
        String requestId
    );

    void completeCommand(UUID commandId, DraftCommandResponse response);

    record NewDraft(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String status,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        JsonNode diagnostics,
        UUID actorId
    ) {
    }

    record UpdateDraft(
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID draftId,
        String status,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        JsonNode diagnostics,
        UUID actorId,
        long expectedAggregateVersion
    ) {
    }

    record DraftCommandStart(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        UUID actorId
    ) {
    }

    record DraftCommandResponse(
        UUID draftId,
        long aggregateVersion,
        String configHash,
        JsonNode payload
    ) {
    }
}
