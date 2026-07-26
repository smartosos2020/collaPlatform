package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublicationCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationPublicationRepository {
    Optional<LockedType> lockType(UUID workspaceId, UUID spaceId, UUID typeId);

    Optional<PublishedConfigurationVersion> findVersion(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    );

    List<PublishedConfigurationVersion> listVersions(UUID workspaceId, UUID spaceId, UUID typeId);

    void insertPublished(NewPublishedVersion version);

    int supersede(UUID workspaceId, UUID spaceId, UUID typeId, UUID versionId);

    int switchCurrent(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID expectedCurrentVersionId,
        UUID nextVersionId,
        UUID actorId
    );

    boolean tryStartCommand(PublicationCommandStart command);

    Optional<PublicationCommandReceipt> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        String operation,
        String requestId
    );

    void completeCommand(UUID commandId, PublicationCommandResponse response);

    record LockedType(UUID currentVersionId, long aggregateVersion, int nextVersionNumber) {
    }

    record NewPublishedVersion(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        int versionNumber,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        UUID sourceDraftId,
        UUID rollbackSourceVersionId,
        UUID actorId
    ) {
    }

    record PublicationCommandStart(
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

    record PublicationCommandResponse(
        UUID versionId,
        int versionNumber,
        String configHash,
        JsonNode payload
    ) {
    }
}
